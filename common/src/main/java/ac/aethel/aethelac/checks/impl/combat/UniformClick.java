package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects uniformly randomized autoclickers.
 *
 * <p>Human click streams are heavy-tailed: bursts of fast clicks and
 * occasional slower clicks give a positive excess kurtosis. Autoclickers
 * that pick a random delay inside a fixed min/max band never leave the band,
 * flattening the distribution towards uniform - an excess kurtosis below
 * zero that sustained human clicking does not produce.</p>
 */
@CheckData(name = "UniformClick", stableKey = "aethel.autoclicker.uniform_click",
        description = "Uniformly randomized click intervals", experimental = true)
public class UniformClick extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 10_000_000_000L;
    private static final int CAPACITY = 200;

    private final double[] intervalsMs = new double[CAPACITY];
    private int sampleCount;
    private int sampleIndex;
    private long lastAttackNanos;

    private long lastFlagNanos;

    private int minSamples;
    private double minCps;
    private double maxIntervalMs;
    private double kurtosisCeiling;

    public UniformClick(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minSamples = Math.max(50, config.getIntElse("attack-patterns.uniform-click-samples", 200));
        minCps = config.getDoubleElse("attack-patterns.uniform-click-min-cps", 5.0D);
        maxIntervalMs = config.getDoubleElse("attack-patterns.uniform-click-max-interval-ms", 1000.0D);
        kurtosisCeiling = config.getDoubleElse("attack-patterns.uniform-click-max-kurtosis", 0.5D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean attack;
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            attack = new WrapperPlayClientInteractEntity(event).getAction()
                    == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        } else if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            attack = true;
        } else {
            return;
        }

        if (!attack) {
            return;
        }

        long now = System.nanoTime();
        if (lastAttackNanos == 0L) {
            lastAttackNanos = now;
            return;
        }

        double intervalMs = (now - lastAttackNanos) / 1_000_000.0D;
        lastAttackNanos = now;

        if (intervalMs > maxIntervalMs) {
            sampleCount = 0;
            sampleIndex = 0;
            return;
        }

        intervalsMs[sampleIndex] = intervalMs;
        sampleIndex = (sampleIndex + 1) % CAPACITY;
        if (sampleCount < CAPACITY) {
            sampleCount++;
        }

        if (sampleCount < minSamples) {
            return;
        }

        evaluate(now);
    }

    private void evaluate(long now) {
        double mean = 0.0D;
        for (int i = 0; i < sampleCount; i++) {
            mean += intervalsMs[i];
        }
        mean /= sampleCount;

        if (mean > 1000.0D / minCps) {
            return;
        }

        double variance = 0.0D;
        double fourth = 0.0D;
        for (int i = 0; i < sampleCount; i++) {
            double diff = intervalsMs[i] - mean;
            double diffSq = diff * diff;
            variance += diffSq;
            fourth += diffSq * diffSq;
        }
        variance /= sampleCount;
        fourth /= sampleCount;

        if (variance <= 0.0D) {
            return;
        }

        double excessKurtosis = fourth / (variance * variance) - 3.0D;
        if (excessKurtosis >= kurtosisCeiling) {
            return;
        }

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        flag(String.format(Locale.ROOT, "%d click intervals, %.2f cps, excess kurtosis %.2f",
                sampleCount, 1000.0D / mean, excessKurtosis));
        lastFlagNanos = now;
        sampleCount = 0;
        sampleIndex = 0;
    }
}
