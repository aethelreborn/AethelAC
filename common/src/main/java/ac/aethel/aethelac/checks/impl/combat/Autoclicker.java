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
 * Detects machine-generated attack cadence from the timing of attack packets.
 *
 * <p>Three conservative signals are evaluated over sliding windows:
 * <ul>
 *   <li>sustained CPS over a full 60 second window (beyond human stamina),</li>
 *   <li>sustained CPS over a 20 second window (beyond human burst limits),</li>
 *   <li>near-zero interval variation (coefficient of variation) across at
 *       least 100 consecutive attacks at an elevated click rate.</li>
 * </ul>
 * A flagged condition re-arms at most once every five seconds so violation
 * weight tracks cheat duration instead of packet count.</p>
 */
@CheckData(name = "Autoclicker", stableKey = "combat.autoclicker_machine_cadence", description = "Attack cadence is beyond sustained human clicking", experimental = false)
public class Autoclicker extends Check implements PacketReceiveListener {

    private static final int CAPACITY = 2048;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long WINDOW_60S = 60_000_000_000L;
    private static final long WINDOW_30S = 30_000_000_000L;
    private static final long WINDOW_20S = 20_000_000_000L;
    private static final double MAX_CADENCE_INTERVAL_MS = 70.0D;

    private final long[] attackTimes = new long[CAPACITY];
    private int ringIndex;
    private int ringSize;
    private long lastFlagNanos;

    private double cps60Threshold;
    private double cps20Threshold;
    private double cadenceCvThreshold;

    public Autoclicker(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        cps60Threshold = config.getDoubleElse("attack-patterns.cps60-threshold", 18.0D);
        cps20Threshold = config.getDoubleElse("attack-patterns.cps20-threshold", 20.0D);
        cadenceCvThreshold = config.getDoubleElse("attack-patterns.cadence-cv-threshold", 0.06D);
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
        recordAttack(System.nanoTime());
    }

    /**
     * Current attacks per second across the requested sliding window.
     * Available to staff tooling and future combat checks.
     */
    public double getCps(int windowSeconds) {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        for (long time : attackTimes) {
            if (time > cutoff) {
                count++;
            }
        }
        return count / (double) windowSeconds;
    }

    private void recordAttack(long now) {
        attackTimes[ringIndex] = now;
        ringIndex = (ringIndex + 1) % CAPACITY;
        if (ringSize < CAPACITY) {
            ringSize++;
        }
        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        evaluate(now);
    }

    private void evaluate(long now) {
        int count60 = 0;
        int count30 = 0;
        int count20 = 0;
        long cut60 = now - WINDOW_60S;
        long cut30 = now - WINDOW_30S;
        long cut20 = now - WINDOW_20S;
        for (long time : attackTimes) {
            if (time <= cut60) {
                continue;
            }
            count60++;
            if (time > cut30) {
                count30++;
            }
            if (time > cut20) {
                count20++;
            }
        }

        if (count60 >= cps60Threshold * 60.0D) {
            flag(String.format(Locale.ROOT, "%.1f CPS over the last 60s (%d attacks)", count60 / 60.0D, count60));
            lastFlagNanos = now;
            return;
        }
        if (count20 >= cps20Threshold * 20.0D) {
            flag(String.format(Locale.ROOT, "%.1f CPS over the last 20s (%d attacks)", count20 / 20.0D, count20));
            lastFlagNanos = now;
            return;
        }
        if (count30 >= 150) {
            evaluateCadence(now);
        }
    }

    private void evaluateCadence(long now) {
        int samples = Math.min(200, ringSize);
        if (samples < 100) {
            return;
        }

        double sum = 0.0D;
        double sumSquares = 0.0D;
        int intervals = 0;
        long previous = 0L;
        boolean first = true;
        for (int k = samples - 1; k >= 0; k--) {
            int index = ringIndex - 1 - k;
            while (index < 0) {
                index += CAPACITY;
            }
            long time = attackTimes[index];
            if (time == 0L) {
                continue;
            }
            if (!first) {
                double delta = (time - previous) / 1.0e6D;
                if (delta >= 0) {
                    sum += delta;
                    sumSquares += delta * delta;
                    intervals++;
                }
            }
            previous = time;
            first = false;
        }

        if (intervals < 99) {
            return;
        }
        double mean = sum / intervals;
        if (mean > MAX_CADENCE_INTERVAL_MS) {
            return;
        }
        double variance = Math.max(0.0D, sumSquares / intervals - mean * mean);
        double cv = Math.sqrt(variance) / mean;
        if (cv < cadenceCvThreshold) {
            flag(String.format(Locale.ROOT, "machine cadence cv=%.3f over %d intervals (%.1f CPS)", cv, intervals, 1000.0D / mean));
            lastFlagNanos = now;
        }
    }
}
