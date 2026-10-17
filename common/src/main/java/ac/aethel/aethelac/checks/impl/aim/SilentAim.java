package ac.aethel.aethelac.checks.impl.aim;

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
 * Detects silent aim rotations that bracket an attack.
 *
 * <p>Silent auras (LiquidBounce's on-tick timing, Vape-style silent aim, teleport
 * auras) send a rotation aimed at the target, attack, and then restore the real
 * mouse rotation in the same tick. The server therefore sees rotation A, the
 * attack, and rotation B where B snaps back to the rotation from before A -
 * something a human cannot do around a click.</p>
 */
@CheckData(name = "SilentAim", stableKey = "aethel.aim.silent_aim",
        description = "Attacked between a silent aim rotation and its revert", experimental = true)
public class SilentAim extends Check implements RotationListener, PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private boolean exempt;

    private boolean hasLastRotation;
    private long lastRotationNanos;
    private float lastOldYaw;
    private float lastOldPitch;
    private float lastNewYaw;
    private float lastNewPitch;

    private boolean pending;
    private float preYaw;
    private float prePitch;
    private float aimYaw;
    private float aimPitch;
    private double aimAngle;
    private long attackNanos;

    private int flips;
    private long firstFlipNanos;
    private long lastFlagNanos;

    private double minAimAngle;
    private double revertAngle;
    private double attackLagMs;
    private double revertWindowMs;
    private int flipsToFlag;
    private double counterWindowSeconds;

    public SilentAim(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minAimAngle = config.getDoubleElse("aim-analysis.silent-flip-min-angle", 25.0D);
        revertAngle = config.getDoubleElse("aim-analysis.silent-revert-angle", 10.0D);
        attackLagMs = config.getDoubleElse("aim-analysis.silent-attack-lag-ms", 40.0D);
        revertWindowMs = config.getDoubleElse("aim-analysis.silent-revert-window-ms", 90.0D);
        flipsToFlag = Math.max(1, config.getIntElse("aim-analysis.silent-flips-to-flag", 2));
        counterWindowSeconds = config.getDoubleElse("aim-analysis.silent-counter-window-seconds", 10.0D);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate
                || player.compensatedEntities.self.getRiding() != null) {
            exempt = true;
            pending = false;
            return;
        }

        if (exempt) {
            exempt = false;
            return;
        }

        long now = System.nanoTime();

        if (pending) {
            pending = false;
            long sinceAttack = now - attackNanos;
            boolean chained = sameRotation(rotationUpdate.oldYaw(), rotationUpdate.oldPitch(), aimYaw, aimPitch);
            if (chained && sinceAttack <= (long) (revertWindowMs * 1_000_000L)) {
                double revert = yawPitchDelta(rotationUpdate.newYaw(), rotationUpdate.newPitch(), preYaw, prePitch);
                if (aimAngle >= minAimAngle && revert <= revertAngle) {
                    recordFlip(now);
                }
            }
        }

        lastOldYaw = rotationUpdate.oldYaw();
        lastOldPitch = rotationUpdate.oldPitch();
        lastNewYaw = rotationUpdate.newYaw();
        lastNewPitch = rotationUpdate.newPitch();
        lastRotationNanos = now;
        hasLastRotation = true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }

        long now = System.nanoTime();
        if (!hasLastRotation || now - lastRotationNanos > (long) (attackLagMs * 1_000_000L)) {
            return;
        }

        double delta = yawPitchDelta(lastNewYaw, lastNewPitch, lastOldYaw, lastOldPitch);
        if (delta < minAimAngle * 0.5D) {
            return;
        }

        preYaw = lastOldYaw;
        prePitch = lastOldPitch;
        aimYaw = lastNewYaw;
        aimPitch = lastNewPitch;
        aimAngle = delta;
        attackNanos = now;
        pending = true;
    }

    private void recordFlip(long now) {
        if (flips == 0 || now - firstFlipNanos > (long) (counterWindowSeconds * 1_000_000_000L)) {
            flips = 0;
            firstFlipNanos = now;
        }
        flips++;

        if (flips >= flipsToFlag && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "silent aim revert: %.1f deg snap then %.1f deg back-to-start (%d flips)",
                    aimAngle, yawPitchDelta(lastNewYaw, lastNewPitch, preYaw, prePitch), flips));
            lastFlagNanos = now;
        }
    }

    private static boolean sameRotation(float yawA, float pitchA, float yawB, float pitchB) {
        return Math.abs(wrapDegrees(yawA - yawB)) < 0.01F && Math.abs(pitchA - pitchB) < 0.01F;
    }

    private static double yawPitchDelta(float yawA, float pitchA, float yawB, float pitchB) {
        double dYaw = wrapDegrees(yawA - yawB);
        double dPitch = pitchA - pitchB;
        return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
