package ac.aethel.aethelac.checks.impl.elytra;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects firework boost automation: elytra boosts fired on a fixed cycle.
 *
 * <p>Super Fire Work style modules boost in timed pulses (fixed period per
 * mode) or whenever speed decays past a threshold, so boosts arrive at
 * machine-constant intervals. Human boosting responds to terrain and plans
 * and never holds one period for a whole flight.</p>
 */
@CheckData(name = "FireworkCadence", stableKey = "aethel.elytra.firework_cadence",
        description = "Firework boosts fired at machine-constant intervals", experimental = true)
public class FireworkCadence extends Check implements PacketReceiveListener {
    private static final int CAPACITY = 16;
    private static final long MIN_INTERVAL_NANOS = 400_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final double[] intervals = new double[CAPACITY];
    private int index;
    private int size;
    private int boostCount;
    private long lastBoostNanos;
    private long lastFlagNanos;

    private int minIntervals;
    private double cadenceCv;
    private double meanMinMs;
    private double meanMaxMs;

    public FireworkCadence(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minIntervals = Math.max(3, config.getIntElse("elytra-analysis.firework-min-intervals", 8));
        cadenceCv = config.getDoubleElse("elytra-analysis.firework-cadence-cv", 0.05D);
        meanMinMs = config.getDoubleElse("elytra-analysis.firework-mean-min-ms", 700.0D);
        meanMaxMs = config.getDoubleElse("elytra-analysis.firework-mean-max-ms", 4000.0D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM || !player.isGliding) {
            return;
        }
        ItemStack held = player.inventory.getHeldItem();
        if (held == null || held.isEmpty() || !held.getType().getName().toString().endsWith(":firework_rocket")) {
            return;
        }

        long now = System.nanoTime();
        boostCount++;
        if (lastBoostNanos != 0L) {
            long gap = now - lastBoostNanos;
            if (gap >= MIN_INTERVAL_NANOS) {
                intervals[index] = gap / 1_000_000.0D;
                index = (index + 1) % CAPACITY;
                if (size < CAPACITY) {
                    size++;
                }
            }
        }
        lastBoostNanos = now;

        if (size < minIntervals || now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        double sum = 0.0D;
        for (double interval : intervals) {
            sum += interval;
        }
        double mean = sum / size;
        if (mean < meanMinMs || mean > meanMaxMs) {
            return;
        }
        double variance = 0.0D;
        for (double interval : intervals) {
            double diff = interval - mean;
            variance += diff * diff;
        }
        double cv = Math.sqrt(variance / size) / mean;
        if (cv <= cadenceCv) {
            flag(String.format(Locale.ROOT, "%d firework boosts, mean %.0fms, cv %.3f", size, mean, cv));
            lastFlagNanos = now;
        }
    }

    public int getBoostCount() {
        return boostCount;
    }
}
