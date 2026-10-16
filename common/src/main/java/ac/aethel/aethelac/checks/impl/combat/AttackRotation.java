package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects attacks that consistently coincide with large crosshair movement.
 *
 * <p>Humans click when their crosshair already covers the target, so the
 * rotation performed on a click tick is usually small. Killaura and 360-degree
 * triggerbots attack as soon as a target is found, so the majority of their
 * attack packets land mid-rotation. The check requires a strong rotating
 * fraction across many attacks in a sustained window before flagging.</p>
 */
@CheckData(name = "AttackRotation", stableKey = "combat.rotating_attack", description = "Attacks while rotating on most clicks", experimental = true, decay = 0.01)
public class AttackRotation extends Check implements PacketReceiveListener, RotationListener {

    private static final int CAPACITY = 256;
    private static final long ATTACK_DEBOUNCE_NANOS = 5_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long ROTATION_FRESH_NANOS = 60_000_000L;

    private final long[] attackTimes = new long[CAPACITY];
    private final boolean[] attackRotating = new boolean[CAPACITY];
    private int index;
    private int size;
    private long lastAttackNanos;
    private long lastFlagNanos;

    private long lastRotationNanos;
    private double lastRotationDelta;

    private int windowSeconds;
    private int minAttacks;
    private double rotatingFraction;
    private double minDelta;

    public AttackRotation(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = config.getIntElse("attack-patterns.rotating-window-seconds", 60);
        minAttacks = config.getIntElse("attack-patterns.rotating-min-attacks", 12);
        rotatingFraction = config.getDoubleElse("attack-patterns.rotating-fraction", 0.8D);
        minDelta = config.getDoubleElse("attack-patterns.rotating-min-delta", 20.0D);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            lastRotationNanos = 0L;
            return;
        }
        lastRotationDelta = Math.hypot(rotationUpdate.deltaYaw(), rotationUpdate.deltaPitch());
        lastRotationNanos = System.nanoTime();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            recordAttack(System.nanoTime());
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }
        recordAttack(System.nanoTime());
    }

    private void recordAttack(long now) {
        // ViaVersion can mirror one click into both the modern Attack packet and
        // a translated interact packet; never count two packets as one click.
        if (now - lastAttackNanos < ATTACK_DEBOUNCE_NANOS) {
            return;
        }
        lastAttackNanos = now;

        boolean rotating = lastRotationNanos != 0L
                && now - lastRotationNanos <= ROTATION_FRESH_NANOS
                && lastRotationDelta >= minDelta;
        attackTimes[index] = now;
        attackRotating[index] = rotating;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

        long cutoff = now - windowSeconds * 1_000_000_000L;
        int total = 0;
        int rotatingCount = 0;
        for (int k = 0; k < size; k++) {
            int slot = (index - 1 - k + CAPACITY * 2) % CAPACITY;
            if (attackTimes[slot] <= cutoff) {
                break;
            }
            total++;
            if (attackRotating[slot]) {
                rotatingCount++;
            }
        }

        if (total < minAttacks || now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        double fraction = rotatingCount / (double) total;
        if (fraction >= rotatingFraction) {
            flag(String.format(Locale.ROOT, "%d/%d attacks while rotating >=%.1f deg/tick in %ds", rotatingCount, total, minDelta, windowSeconds));
            lastFlagNanos = now;
        }
    }

    public double getRotatingFraction() {
        long now = System.nanoTime();
        long cutoff = now - windowSeconds * 1_000_000_000L;
        int total = 0;
        int rotatingCount = 0;
        for (int k = 0; k < size; k++) {
            int slot = (index - 1 - k + CAPACITY * 2) % CAPACITY;
            if (attackTimes[slot] <= cutoff) {
                break;
            }
            total++;
            if (attackRotating[slot]) {
                rotatingCount++;
            }
        }
        return total == 0 ? 0.0D : rotatingCount / (double) total;
    }
}
