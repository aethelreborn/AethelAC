package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects inventory automation: container clicks sustained at superhuman rate.
 *
 * <p>Chest stealers and inventory tweaks click a pickup or shift-move every
 * tick or two for the whole container; human looting settles around two to
 * five clicks per second even for practiced players. Drag clicks and other
 * window click modes are ignored so machine click widening stays out of the
 * sample. A second tier catches menu bots that navigate GUIs at a fixed
 * delay: twenty consecutive clicks with near-constant intervals in the
 * 250-900 ms band.</p>
 */
@CheckData(name = "FastClick", stableKey = "aethel.inventory.fast_click", description = "Clicked in containers at superhuman rate", experimental = true)
public class FastClick extends Check implements PacketReceiveListener {

    private static final int CAPACITY = 512;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long ARMOR_WINDOW_NANOS = 400_000_000L;
    private static final int ARMOR_MIN_CLICKS = 4;
    private static final int CADENCE_SAMPLES = 20;

    private final long[] clicks = new long[CAPACITY];
    private int index;
    private int size;
    private long lastFlagNanos;

    private final long[] armorClicks = new long[16];
    private int armorIndex;
    private int armorSize;

    private final double[] intervals = new double[CADENCE_SAMPLES];
    private int intervalIndex;
    private int cadenceSize;
    private long lastClickNanos;

    private int windowSeconds;
    private int minCount;
    private double meanIntervalMs;
    private double cadenceCv;
    private double cadenceMinMs;
    private double cadenceMaxMs;

    public FastClick(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = config.getIntElse("inventory-automation.click-window-seconds", 10);
        minCount = config.getIntElse("inventory-automation.click-min-count", 60);
        meanIntervalMs = config.getDoubleElse("inventory-automation.click-mean-interval-ms", 110.0D);
        cadenceCv = config.getDoubleElse("inventory-automation.menu-cadence-cv", 0.045D);
        cadenceMinMs = config.getDoubleElse("inventory-automation.menu-min-interval-ms", 250.0D);
        cadenceMaxMs = config.getDoubleElse("inventory-automation.menu-max-interval-ms", 900.0D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
            return;
        }
        WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
        WindowClickType mode = click.getWindowClickType();
        if (mode != WindowClickType.PICKUP && mode != WindowClickType.QUICK_MOVE) {
            return;
        }
        long now = System.nanoTime();
        if (recordArmorClick(click.getSlot(), now)) {
            return;
        }
        recordClick(now);
    }

    private void recordClick(long now) {
        clicks[index] = now;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

        recordCadence(now);

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        long cutoff = now - windowSeconds * 1_000_000_000L;
        int count = 0;
        long oldest = now;
        for (int k = 0; k < size; k++) {
            long time = clicks[(index - 1 - k + CAPACITY * 2) % CAPACITY];
            if (time <= cutoff) {
                break;
            }
            count++;
            oldest = time;
        }
        if (count < minCount || count < 2) {
            return;
        }
        double mean = (now - oldest) / 1_000_000.0D / (count - 1);
        if (mean <= meanIntervalMs) {
            flag(String.format(Locale.ROOT, "%d container clicks in %ds (avg every %.0fms)", count, windowSeconds, mean));
            lastFlagNanos = now;
        }
    }

    private void recordCadence(long now) {
        if (lastClickNanos != 0L) {
            intervals[intervalIndex] = (now - lastClickNanos) / 1_000_000.0D;
            intervalIndex = (intervalIndex + 1) % CADENCE_SAMPLES;
            if (cadenceSize < CADENCE_SAMPLES) {
                cadenceSize++;
            }
        }
        lastClickNanos = now;

        if (cadenceSize < CADENCE_SAMPLES || now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        double sum = 0.0D;
        for (double interval : intervals) {
            sum += interval;
        }
        double mean = sum / CADENCE_SAMPLES;
        if (mean < cadenceMinMs || mean > cadenceMaxMs) {
            return;
        }
        double variance = 0.0D;
        for (double interval : intervals) {
            double diff = interval - mean;
            variance += diff * diff;
        }
        double cv = Math.sqrt(variance / CADENCE_SAMPLES) / mean;
        if (cv <= cadenceCv) {
            flag(String.format(Locale.ROOT, "menu cadence %d clicks, mean %.0fms, cv %.3f", CADENCE_SAMPLES, mean, cv));
            lastFlagNanos = now;
        }
    }

    private boolean recordArmorClick(int slot, long now) {
        if (slot < 5 || slot > 8) {
            return false;
        }
        armorClicks[armorIndex] = now;
        armorIndex = (armorIndex + 1) % armorClicks.length;
        if (armorSize < armorClicks.length) {
            armorSize++;
        }
        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return true;
        }
        long cutoff = now - ARMOR_WINDOW_NANOS;
        int count = 0;
        for (int k = 0; k < armorSize; k++) {
            if (armorClicks[(armorIndex - 1 - k + armorClicks.length * 2) % armorClicks.length] <= cutoff) {
                break;
            }
            count++;
        }
        if (count >= ARMOR_MIN_CLICKS) {
            flag(String.format(Locale.ROOT, "%d armor slot clicks in %dms", count, ARMOR_WINDOW_NANOS / 1_000_000L));
            lastFlagNanos = now;
        }
        return true;
    }

    public int getWindowClickCount() {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        for (int k = 0; k < size; k++) {
            if (clicks[(index - 1 - k + CAPACITY * 2) % CAPACITY] <= cutoff) {
                break;
            }
            count++;
        }
        return count;
    }
}
