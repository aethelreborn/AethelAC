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
 * Detects attack-anchored rotation flicks that return to their starting
 * direction within a few ticks.
 *
 * <p>Silent aim rotates the server-side view toward the target to attack and
 * then restores the previous heading. When the attack is anchored, this shows
 * as: a rotation burst into the attack, followed shortly after by a rotation
 * of similar magnitude roughly opposite to the burst, ending near the heading
 * the burst started from. Humans making aim corrections overshoot and correct
 * in the same direction; they rarely reverse most of a burst and land back on
 * the origin heading within a few ticks, repeatedly, around their attacks.</p>
 */
@CheckData(name = "ReturnFlick", stableKey = "aethel.aim.return_flick", description = "Rotation burst returns to origin around attacks", experimental = true, decay = 0.01)
public class ReturnFlick extends Check implements PacketReceiveListener, RotationListener {

    private static final int CAPACITY = 64;
    private static final long ATTACK_DEBOUNCE_NANOS = 5_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long APPROACH_WINDOW_NANOS = 120_000_000L;
    private static final long APPROACH_EXCLUDE_NANOS = 10_000_000L;
    private static final long REVERSAL_WINDOW_NANOS = 150_000_000L;
    private static final double MIN_COSINE = -0.5D;

    private final long[] rotTimes = new long[CAPACITY];
    private final double[] rotYaws = new double[CAPACITY];
    private final double[] rotPitches = new double[CAPACITY];
    private int rotIndex;
    private int rotSize;

    private final long[] verdictTimes = new long[CAPACITY];
    private final boolean[] verdictMatched = new boolean[CAPACITY];
    private int verdictIndex;
    private int verdictSize;

    private boolean pending;
    private long pendingAttackNanos;
    private double approachYaw;
    private double approachPitch;
    private double returnYaw;
    private double returnPitch;

    private long lastAttackNanos;
    private long lastFlagNanos;

    private double approachMin;
    private double ratioMin;
    private double endpointTolerance;
    private int windowAttacks;
    private int windowSeconds;
    private double fraction;

    public ReturnFlick(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        approachMin = config.getDoubleElse("attack-patterns.return-approach-min", 6.0D);
        ratioMin = config.getDoubleElse("attack-patterns.return-ratio-min", 0.6D);
        endpointTolerance = config.getDoubleElse("attack-patterns.return-endpoint-tolerance", 0.35D);
        windowAttacks = config.getIntElse("attack-patterns.return-window-attacks", 12);
        windowSeconds = config.getIntElse("attack-patterns.return-window-seconds", 20);
        fraction = config.getDoubleElse("attack-patterns.return-fraction", 0.6D);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            pending = false;
            return;
        }

        double deltaYaw = rotationUpdate.deltaYaw();
        double deltaPitch = rotationUpdate.deltaPitch();
        long now = System.nanoTime();

        if (pending) {
            long sinceAttack = now - pendingAttackNanos;
            if (sinceAttack > REVERSAL_WINDOW_NANOS) {
                finalizeVerdict(now);
            } else if (sinceAttack > 0) {
                returnYaw += deltaYaw;
                returnPitch += deltaPitch;
            }
        }

        rotTimes[rotIndex] = now;
        rotYaws[rotIndex] = deltaYaw;
        rotPitches[rotIndex] = deltaPitch;
        rotIndex = (rotIndex + 1) % CAPACITY;
        if (rotSize < CAPACITY) {
            rotSize++;
        }
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

        if (pending) {
            finalizeVerdict(now);
        }

        approachYaw = 0.0D;
        approachPitch = 0.0D;
        for (int k = 0; k < rotSize; k++) {
            int slot = (rotIndex - 1 - k + CAPACITY * 2) % CAPACITY;
            long age = now - rotTimes[slot];
            if (age > APPROACH_WINDOW_NANOS) {
                break;
            }
            if (age > APPROACH_EXCLUDE_NANOS) {
                approachYaw += rotYaws[slot];
                approachPitch += rotPitches[slot];
            }
        }

        pending = true;
        pendingAttackNanos = now;
        returnYaw = 0.0D;
        returnPitch = 0.0D;
    }

    private void finalizeVerdict(long now) {
        pending = false;
        double magnitudeApproach = Math.hypot(approachYaw, approachPitch);
        if (magnitudeApproach < approachMin) {
            return;
        }

        double magnitudeReturn = Math.hypot(returnYaw, returnPitch);
        boolean matched = false;
        if (magnitudeReturn >= ratioMin * magnitudeApproach) {
            double cosine = (approachYaw * returnYaw + approachPitch * returnPitch)
                    / (magnitudeApproach * magnitudeReturn);
            double endMagnitude = Math.hypot(approachYaw + returnYaw, approachPitch + returnPitch);
            matched = cosine <= MIN_COSINE && endMagnitude <= endpointTolerance * magnitudeApproach;
        }

        verdictTimes[verdictIndex] = now;
        verdictMatched[verdictIndex] = matched;
        verdictIndex = (verdictIndex + 1) % CAPACITY;
        if (verdictSize < CAPACITY) {
            verdictSize++;
        }

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        long cutoff = now - windowSeconds * 1_000_000_000L;
        int total = 0;
        int returned = 0;
        for (int k = 0; k < verdictSize; k++) {
            int slot = (verdictIndex - 1 - k + CAPACITY * 2) % CAPACITY;
            if (verdictTimes[slot] <= cutoff) {
                break;
            }
            total++;
            if (verdictMatched[slot]) {
                returned++;
            }
        }
        if (total >= windowAttacks && returned / (double) total >= fraction) {
            flag(String.format(Locale.ROOT, "%d/%d attacks preceded by a returned rotation burst in %ds", returned, total, windowSeconds));
            lastFlagNanos = now;
        }
    }
}
