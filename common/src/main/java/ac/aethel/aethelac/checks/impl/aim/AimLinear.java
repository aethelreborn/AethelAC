package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.api.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects machine-constant angular velocity: aim smoothing implementations
 * (linear interpolation rotators) advance the crosshair by a clamped, near
 * identical amount every tick while tracking. Human mouse input never holds
 * a coefficient of variation below a few percent for fifteen consecutive
 * ticks of active rotation.
 *
 * <p>Three independent series are tracked: the combined rotation speed, the
 * yaw speed alone (catches rotators that vary pitch), and the pitch speed
 * alone (catches clamp-based pitch correctors). Only consecutive per-tick
 * deltas above the configured combat speeds are considered; slower movement,
 * pauses, teleports and vehicle switches reset the windows.</p>
 */
@CheckData(name = "LinearRotation", stableKey = "aethel.aim.linear_rotation", description = "Rotated at machine-constant angular velocity", experimental = true, decay = 0.01)
public class AimLinear extends Check implements RotationListener {

    private static final int WINDOW = 15;
    private static final double MAX_SPEED_DEG = 60.0D;
    private static final long MIN_UPDATE_GAP_NANOS = 40_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final Series norm = new Series();
    private final Series yaw = new Series();
    private final Series pitch = new Series();
    private long lastUpdateNanos;
    private long lastFlagNanos;

    private double cvThreshold;
    private double minSpeed;
    private double yawCvThreshold;
    private double yawMinSpeed;
    private double pitchCvThreshold;
    private double pitchMinSpeed;

    public AimLinear(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        cvThreshold = config.getDoubleElse("aim-analysis.linear-cv-threshold", 0.05D);
        minSpeed = config.getDoubleElse("aim-analysis.linear-min-speed", 3.0D);
        yawCvThreshold = config.getDoubleElse("aim-analysis.yaw-cv-threshold", 0.05D);
        yawMinSpeed = config.getDoubleElse("aim-analysis.yaw-min-speed", 2.0D);
        pitchCvThreshold = config.getDoubleElse("aim-analysis.pitch-cv-threshold", 0.06D);
        pitchMinSpeed = config.getDoubleElse("aim-analysis.pitch-min-speed", 0.8D);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        // Exempt for teleport, entering a vehicle due to rotation reset, and
        // forced client-side rotation after interacting with a horse
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            resetAll();
            return;
        }

        long now = System.nanoTime();
        if (lastUpdateNanos != 0 && now - lastUpdateNanos < MIN_UPDATE_GAP_NANOS) {
            return;
        }
        lastUpdateNanos = now;

        double deltaYaw = rotationUpdate.deltaYaw();
        double deltaPitch = rotationUpdate.deltaPitch();
        double speed = Math.hypot(deltaYaw, deltaPitch);
        if (speed > MAX_SPEED_DEG) {
            resetAll();
            return;
        }

        Double normCv = speed >= minSpeed ? norm.offer(speed) : norm.resetAndReturnNull();
        Double yawCv = Math.abs(deltaYaw) >= yawMinSpeed ? yaw.offer(Math.abs(deltaYaw)) : yaw.resetAndReturnNull();
        Double pitchCv = Math.abs(deltaPitch) >= pitchMinSpeed ? pitch.offer(Math.abs(deltaPitch)) : pitch.resetAndReturnNull();

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        if (normCv != null && normCv <= cvThreshold) {
            flag(String.format(Locale.ROOT, "constant-velocity rotation cv=%.4f over %d ticks", normCv, WINDOW));
            lastFlagNanos = now;
        } else if (yawCv != null && yawCv <= yawCvThreshold) {
            flag(String.format(Locale.ROOT, "constant-velocity yaw cv=%.4f over %d ticks", yawCv, WINDOW));
            lastFlagNanos = now;
        } else if (pitchCv != null && pitchCv <= pitchCvThreshold) {
            flag(String.format(Locale.ROOT, "constant-velocity pitch cv=%.4f over %d ticks", pitchCv, WINDOW));
            lastFlagNanos = now;
        }
    }

    private void resetAll() {
        norm.reset();
        yaw.reset();
        pitch.reset();
        lastUpdateNanos = 0L;
    }

    private static final class Series {
        private final double[] values = new double[WINDOW];
        private int index;
        private int filled;

        Double offer(double value) {
            values[index] = value;
            index = (index + 1) % WINDOW;
            if (filled < WINDOW) {
                filled++;
            }
            if (filled < WINDOW) {
                return null;
            }
            double sum = 0.0D;
            for (double v : values) {
                sum += v;
            }
            double mean = sum / WINDOW;
            double sumSquares = 0.0D;
            for (double v : values) {
                sumSquares += (v - mean) * (v - mean);
            }
            return Math.sqrt(sumSquares / WINDOW) / mean;
        }

        Double resetAndReturnNull() {
            reset();
            return null;
        }

        void reset() {
            index = 0;
            filled = 0;
        }
    }
}
