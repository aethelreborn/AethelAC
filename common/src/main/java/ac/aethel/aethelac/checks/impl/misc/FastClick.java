package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.reflection.GeyserUtil;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
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
 * 110-900 ms band - the variance, not the pace, is the machine criterion,
 * human item selection injects tens of milliseconds of jitter even when the
 * raw rate is fast.</p>
 *
 * <p>A third tier catches zero-reaction looting (instaloot and loot-aura
 * modules). A click that references a freshly opened container id can only
 * have been produced by a client that already received the open packet, so
 * the server-side gap between sending the open window and receiving the
 * first click is at least one round trip: a module reacting to the packet
 * shows an "apparent reaction" (gap minus the round-trip floor) of about
 * zero, while any human input needs a visual reaction first. Three or more
 * such clicks inside the counter window flag. The round-trip floor is the
 * minimum of recent transaction and keepalive pings, a lower bound of the
 * current round trip unless the player's link improves by tens of
 * milliseconds between samples. Blind mashing through *server-driven* menus
 * (opens the player did not cause with their own click) is the accepted
 * false-positive class; bedrock (Geyser / Floodgate) players are exempt
 * because their GUI semantics differ.</p>
 */
@CheckData(name = "FastClick", stableKey = "aethel.inventory.fast_click", description = "Clicked in containers at superhuman rate", experimental = true)
public class FastClick extends Check implements PacketReceiveListener, PacketSendListener {

    private static final int CAPACITY = 512;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long ARMOR_WINDOW_NANOS = 400_000_000L;
    private static final int ARMOR_MIN_CLICKS = 4;
    private static final int CADENCE_SAMPLES = 20;
    private static final long RTT_MEMORY_NANOS = 30_000_000_000L;

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

    private long openSentNanos;
    private int openContainerId = -1;
    private int lastOpenContainerId = -1;
    private boolean reactionArmed;
    private boolean firstClickSeen;
    private int reactionHits;
    private long reactionFirstNanos;
    private int rttFloorMs = Integer.MAX_VALUE;
    private long rttSampleNanos;
    private Boolean bedrock;

    private int windowSeconds;
    private int minCount;
    private double meanIntervalMs;
    private double cadenceCv;
    private double cadenceMinMs;
    private double cadenceMaxMs;
    private double reactionMinMs;
    private int reactionMinEvents;
    private long reactionCounterNanos;
    private int reactionMaxPingMs;

    public FastClick(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = config.getIntElse("inventory-automation.click-window-seconds", 10);
        minCount = config.getIntElse("inventory-automation.click-min-count", 60);
        meanIntervalMs = config.getDoubleElse("inventory-automation.click-mean-interval-ms", 110.0D);
        cadenceCv = config.getDoubleElse("inventory-automation.menu-cadence-cv", 0.045D);
        cadenceMinMs = config.getDoubleElse("inventory-automation.menu-min-interval-ms", 110.0D);
        cadenceMaxMs = config.getDoubleElse("inventory-automation.menu-max-interval-ms", 900.0D);
        reactionMinMs = Math.max(5.0D, config.getDoubleElse("inventory-automation.click-reaction-min-ms", 70.0D));
        reactionMinEvents = Math.max(2, config.getIntElse("inventory-automation.click-reaction-min-events", 3));
        reactionCounterNanos = Math.max(1, config.getIntElse("inventory-automation.click-reaction-counter-seconds", 10)) * 1_000_000_000L;
        reactionMaxPingMs = Math.max(50, config.getIntElse("inventory-automation.click-reaction-max-ping-ms", 300));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            int containerId = new WrapperPlayServerOpenWindow(event).getContainerId();
            long now = System.nanoTime();
            updateRttFloor(now);
            openSentNanos = now;
            openContainerId = containerId;
            // A re-sent open for a container the client already had displayed
            // does not force a round trip, so its first click is not judged.
            reactionArmed = containerId != lastOpenContainerId;
            lastOpenContainerId = containerId;
            firstClickSeen = false;
        } else if (event.getPacketType() == PacketType.Play.Server.CLOSE_WINDOW) {
            closeWindow();
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            closeWindow();
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
            return;
        }
        WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
        checkWindowReaction(click.getWindowId());
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

    private void closeWindow() {
        openSentNanos = 0L;
        openContainerId = -1;
        reactionArmed = false;
        firstClickSeen = false;
    }

    private void checkWindowReaction(int windowId) {
        if (!reactionArmed || firstClickSeen || openSentNanos == 0L
                || windowId != openContainerId || rttFloorMs > reactionMaxPingMs) {
            return;
        }
        firstClickSeen = true;
        if (bedrock()) {
            return;
        }

        long now = System.nanoTime();
        double deltaMs = (now - openSentNanos) / 1_000_000.0D;
        if (deltaMs - rttFloorMs >= reactionMinMs) {
            return;
        }

        if (reactionFirstNanos == 0L || now - reactionFirstNanos > reactionCounterNanos) {
            reactionHits = 0;
            reactionFirstNanos = now;
        }
        reactionHits++;

        if (reactionHits >= reactionMinEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "first container click %.0f ms after the open (zero-reaction looting, ping floor %d ms)",
                    deltaMs, rttFloorMs));
            lastFlagNanos = now;
            reactionHits = 0;
            reactionFirstNanos = 0L;
        }
    }

    private void updateRttFloor(long now) {
        int sample = player.getTransactionPing();
        int keepAlive = player.getKeepAlivePing();
        if (keepAlive > 0) {
            sample = Math.min(sample, keepAlive);
        }
        if (rttSampleNanos == 0L || now - rttSampleNanos > RTT_MEMORY_NANOS) {
            rttFloorMs = sample;
        } else {
            rttFloorMs = Math.min(rttFloorMs, sample);
        }
        rttSampleNanos = now;
    }

    private boolean bedrock() {
        if (bedrock == null) {
            boolean result = false;
            try {
                result = GeyserUtil.isBedrockPlayer(player.uuid);
            } catch (Throwable ignored) {
                // Geyser/Floodgate API absent or unhappy - treat as a Java player.
            }
            bedrock = result;
        }
        return bedrock;
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
