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
 * sample.</p>
 */
@CheckData(name = "FastClick", stableKey = "aethel.inventory.fast_click", description = "Clicked in containers at superhuman rate", experimental = true)
public class FastClick extends Check implements PacketReceiveListener {

    private static final int CAPACITY = 512;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] clicks = new long[CAPACITY];
    private int index;
    private int size;
    private long lastFlagNanos;

    private int windowSeconds;
    private int minCount;
    private double meanIntervalMs;

    public FastClick(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = config.getIntElse("inventory-automation.click-window-seconds", 10);
        minCount = config.getIntElse("inventory-automation.click-min-count", 60);
        meanIntervalMs = config.getDoubleElse("inventory-automation.click-mean-interval-ms", 110.0D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
            return;
        }
        WindowClickType mode = new WrapperPlayClientClickWindow(event).getWindowClickType();
        if (mode != WindowClickType.PICKUP && mode != WindowClickType.QUICK_MOVE) {
            return;
        }
        recordClick(System.nanoTime());
    }

    private void recordClick(long now) {
        clicks[index] = now;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

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
