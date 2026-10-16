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
 * Detects slow metronome cadence: attack intervals at a human-plausible rate
 * (under the Autoclicker windows) that are nevertheless machine-perfect.
 * Triggerbots with a fixed attack delay and timed macros produce intervals
 * with near-zero relative variation across dozens of clicks; hand clicking
 * carries tens of milliseconds of jitter per interval.
 */
@CheckData(name = "SlowCadence", stableKey = "combat.attack_metronome", description = "Attacks at a machine-constant slow cadence", experimental = true, decay = 0.01)
public class SlowCadence extends Check implements PacketReceiveListener {

    private static final int CAPACITY = 128;
    private static final long ATTACK_DEBOUNCE_NANOS = 5_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final double MIN_INTERVAL_MS = 50.0D;
    private static final double MIN_MEAN_INTERVAL_MS = 80.0D;

    private final double[] intervals = new double[CAPACITY];
    private int intervalIndex;
    private int intervalCount;
    private long lastAttackNanos;
    private long lastFlagNanos;

    private int metronomeSamples;
    private double metronomeCv;
    private double metronomeMaxInterval;

    public SlowCadence(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        metronomeSamples = config.getIntElse("attack-patterns.metronome-samples", 48);
        metronomeCv = config.getDoubleElse("attack-patterns.metronome-cv", 0.035D);
        metronomeMaxInterval = config.getDoubleElse("attack-patterns.metronome-max-interval", 400.0D);
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
        if (lastAttackNanos != 0L) {
            intervals[intervalIndex] = (now - lastAttackNanos) / 1_000_000.0D;
            intervalIndex = (intervalIndex + 1) % CAPACITY;
            if (intervalCount < CAPACITY) {
                intervalCount++;
            }
        }
        lastAttackNanos = now;

        int samples = Math.min(metronomeSamples, intervalCount);
        if (samples < 8 || now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        double sum = 0.0D;
        for (int k = 0; k < samples; k++) {
            double interval = intervals[(intervalIndex - 1 - k + CAPACITY * 2) % CAPACITY];
            if (interval < MIN_INTERVAL_MS || interval > metronomeMaxInterval) {
                return;
            }
            sum += interval;
        }
        double mean = sum / samples;
        if (mean < MIN_MEAN_INTERVAL_MS) {
            return;
        }
        double variance = 0.0D;
        for (int k = 0; k < samples; k++) {
            double interval = intervals[(intervalIndex - 1 - k + CAPACITY * 2) % CAPACITY];
            double diff = interval - mean;
            variance += diff * diff;
        }
        double cv = Math.sqrt(variance / samples) / mean;
        if (cv <= metronomeCv) {
            flag(String.format(Locale.ROOT, "metronome cadence: %d intervals, mean %.1fms, cv %.4f", samples, mean, cv));
            lastFlagNanos = now;
        }
    }
}
