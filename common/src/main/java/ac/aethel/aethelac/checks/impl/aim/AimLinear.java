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
 * <p>Only consecutive per-tick rotation deltas above the configured combat
 * speed are considered; slower movement, pauses, teleports and vehicle
 * switches reset the window.</p>
 */
@CheckData(name = "LinearRotation", stableKey = "aethel.aim.linear_rotation", description = "Rotated at machine-constant angular velocity", experimental = true, decay = 0.01)
public class AimLinear extends Check implements RotationListener {

    private static final int WINDOW = 15;
    private static final double MAX_SPEED_DEG = 60.0D;
    private static final long MIN_UPDATE_GAP_NANOS = 40_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final double[] deltas = new double[WINDOW];
    private int index;
    private int filled;
    private long lastUpdateNanos;
    private long lastFlagNanos;

    private double cvThreshold;
    private double minSpeed;

    public AimLinear(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        cvThreshold = config.getDoubleElse("aim-analysis.linear-cv-threshold", 0.05D);
        minSpeed = config.getDoubleElse("aim-analysis.linear-min-speed", 3.0D);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        // Exempt for teleport, entering a vehicle due to rotation reset, and
        // forced client-side rotation after interacting with a horse
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            resetWindow();
            return;
        }

        long now = System.nanoTime();
        if (lastUpdateNanos != 0 && now - lastUpdateNanos < MIN_UPDATE_GAP_NANOS) {
            return;
        }
        lastUpdateNanos = now;

        double speed = Math.hypot(rotationUpdate.deltaYaw(), rotationUpdate.deltaPitch());
        if (speed < minSpeed || speed > MAX_SPEED_DEG) {
            resetWindow();
            return;
        }

        deltas[index] = speed;
        index = (index + 1) % WINDOW;
        if (filled < WINDOW) {
            filled++;
        }
        if (filled < WINDOW) {
            return;
        }

        double sum = 0.0D;
        for (double delta : deltas) {
            sum += delta;
        }
        double mean = sum / WINDOW;
        double sumSquares = 0.0D;
        for (double delta : deltas) {
            sumSquares += (delta - mean) * (delta - mean);
        }
        double cv = Math.sqrt(sumSquares / WINDOW) / mean;

        if (cv <= cvThreshold) {
            if (now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
                flag(String.format(Locale.ROOT, "constant-velocity rotation cv=%.4f mean=%.2f deg/tick over %d ticks", cv, mean, WINDOW));
                lastFlagNanos = now;
            }
        } else {
            reward();
        }
    }

    private void resetWindow() {
        index = 0;
        filled = 0;
        lastUpdateNanos = 0L;
    }
}
