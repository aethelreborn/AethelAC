package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects metronome jump tapping (W-tap / D-tap macro modules).
 *
 * <p>Humans hold or rhythmically tap jump with drifting intervals; macro
 * modules emit one-tick pulses on a fixed tick schedule. A run of short
 * pulses whose intervals quantize to a machine-constant cadence is
 * automation, not input.</p>
 */
@CheckData(name = "JumpCadence", stableKey = "aethel.misc.jump_cadence",
        description = "Machine-cadence jump tapping", experimental = true)
public class JumpCadence extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int CAPACITY = 16;

    private final int[] intervals = new int[CAPACITY];
    private final int[] durations = new int[CAPACITY];
    private int sampleCount;
    private int sampleIndex;

    private int lastSampledTick = -1;
    private boolean lastInput;
    private int pulseStartTick = -1;
    private int lastPulseEndTick = -1;

    private long lastFlagNanos;

    private int maxPulseTicks;
    private int minSamples;
    private double intervalMaxCv;
    private int maxIntervalTicks;

    public JumpCadence(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxPulseTicks = Math.max(1, config.getIntElse("jump-analysis.jump-max-pulse-ticks", 2));
        minSamples = Math.max(6, config.getIntElse("jump-analysis.jump-min-pulses", 12));
        intervalMaxCv = config.getDoubleElse("jump-analysis.jump-interval-cv", 0.12D);
        maxIntervalTicks = Math.max(4, config.getIntElse("jump-analysis.jump-max-interval-ticks", 40));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isTickPacket(event.getPacketType()) || !player.cameraEntity.isSelf()) {
            pulseStartTick = -1;
            return;
        }

        int tick = AethelAPI.INSTANCE.getTickManager().currentTick;
        if (tick == lastSampledTick) {
            return;
        }
        lastSampledTick = tick;

        boolean jump = player.isJumping;
        if (jump && !lastInput) {
            pulseStartTick = tick;
        } else if (!jump && lastInput && pulseStartTick >= 0) {
            int duration = tick - pulseStartTick;
            if (duration <= maxPulseTicks && lastPulseEndTick >= 0) {
                int interval = pulseStartTick - lastPulseEndTick;
                if (interval <= maxIntervalTicks) {
                    intervals[sampleIndex] = interval;
                    durations[sampleIndex] = duration;
                    sampleIndex = (sampleIndex + 1) % CAPACITY;
                    if (sampleCount < CAPACITY) {
                        sampleCount++;
                    }
                    evaluate();
                }
            }
            lastPulseEndTick = tick;
        }
        lastInput = jump;
    }

    private void evaluate() {
        if (sampleCount < minSamples) {
            return;
        }

        double mean = 0.0D;
        for (int i = 0; i < sampleCount; i++) {
            mean += intervals[i];
        }
        mean /= sampleCount;

        double variance = 0.0D;
        for (int i = 0; i < sampleCount; i++) {
            double diff = intervals[i] - mean;
            variance += diff * diff;
        }
        double cv = mean > 0.0D ? Math.sqrt(variance / sampleCount) / mean : 0.0D;

        if (cv > intervalMaxCv) {
            return;
        }

        int shortPulses = 0;
        for (int i = 0; i < sampleCount; i++) {
            if (durations[i] <= maxPulseTicks) {
                shortPulses++;
            }
        }
        if ((double) shortPulses / sampleCount < 0.9D) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        flag(String.format(Locale.ROOT, "%d jump pulses every %.1f ticks (%.2f cv)",
                sampleCount, mean, cv));
        lastFlagNanos = now;
        sampleCount = 0;
        sampleIndex = 0;
    }
}
