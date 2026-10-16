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
 * Detects machine-geometric rotation deceleration: rotation smoothing
 * implementations scale the remaining angle by a constant factor every tick
 * ({@code step = delta * speed}), so consecutive per-tick deltas form a
 * geometric series. This is the signature of silent-aim return-to-camera
 * smoothing and ease-out rotators.
 *
 * <p>Human mouse movement decelerates in two to three ticks with variable
 * ratios, never six consecutive ticks of near-constant decay ratio.</p>
 */
@CheckData(name = "RotationDecay", stableKey = "aethel.aim.rotation_decay", description = "Rotated with machine-geometric deceleration", experimental = true, decay = 0.01)
public class RotationDecay extends Check implements RotationListener {

    private static final int SERIES = 6;
    private static final double MIN_END_DEG = 0.15D;
    private static final double MAX_SPEED_DEG = 60.0D;
    private static final double MAX_DECAY_FACTOR = 0.5D;
    private static final long MIN_UPDATE_GAP_NANOS = 40_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final double[] deltas = new double[SERIES];
    private int index;
    private int filled;
    private long lastUpdateNanos;
    private long lastFlagNanos;

    private double ratioMin;
    private double ratioMax;
    private double ratioSpread;
    private double minStart;

    public RotationDecay(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        ratioMin = config.getDoubleElse("aim-analysis.decay-ratio-min", 0.80D);
        ratioMax = config.getDoubleElse("aim-analysis.decay-ratio-max", 0.995D);
        ratioSpread = config.getDoubleElse("aim-analysis.decay-ratio-spread", 0.06D);
        minStart = config.getDoubleElse("aim-analysis.decay-min-start", 6.0D);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        // Exempt for teleport, entering a vehicle due to rotation reset, and
        // forced client-side rotation after interacting with a horse
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            reset();
            return;
        }

        long now = System.nanoTime();
        if (lastUpdateNanos != 0 && now - lastUpdateNanos < MIN_UPDATE_GAP_NANOS) {
            return;
        }
        lastUpdateNanos = now;

        double speed = Math.hypot(rotationUpdate.deltaYaw(), rotationUpdate.deltaPitch());
        if (speed > MAX_SPEED_DEG) {
            reset();
            return;
        }
        if (speed < MIN_END_DEG) {
            reset();
            return;
        }

        deltas[index] = speed;
        index = (index + 1) % SERIES;
        if (filled < SERIES) {
            filled++;
        }
        if (filled < SERIES) {
            return;
        }

        double previous = 0.0D;
        double first = 0.0D;
        double last = 0.0D;
        double ratioMinSeen = Double.MAX_VALUE;
        double ratioMaxSeen = 0.0D;
        for (int k = 0; k < SERIES; k++) {
            double value = deltas[(index + k) % SERIES];
            if (k == 0) {
                first = value;
            } else {
                double ratio = value / previous;
                if (ratio < ratioMin || ratio > ratioMax) {
                    return;
                }
                if (ratio < ratioMinSeen) {
                    ratioMinSeen = ratio;
                }
                if (ratio > ratioMaxSeen) {
                    ratioMaxSeen = ratio;
                }
            }
            previous = value;
            last = value;
        }

        if (first < minStart || last > first * MAX_DECAY_FACTOR) {
            return;
        }
        if (ratioMaxSeen - ratioMinSeen > ratioSpread) {
            return;
        }
        if (now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "geometric rotation decay %.2f->%.2f deg/tick, ratio %.3f-%.3f", first, last, ratioMinSeen, ratioMaxSeen));
            lastFlagNanos = now;
        }
    }

    private void reset() {
        index = 0;
        filled = 0;
        lastUpdateNanos = 0L;
    }
}
