package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects hotbar tool automation: starting a dig and immediately cycling the
 * selected slot to a tool and back within a few ticks, repeatedly.
 *
 * <p>AutoTool modules swap to the best tool the instant a dig starts and
 * restore the previous slot as soon as the block finishes, producing a
 * characteristic switch swap cycle with a sub-second turnaround every time
 * they break a block. Humans who mine with the wrong tool in hand switch and
 * keep the tool selected rather than restoring the old slot after every
 * block.</p>
 */
@CheckData(name = "AutoToolChain", stableKey = "aethel.inventory.auto_tool_chain", description = "Cycled hotbar slots around every dig", experimental = true)
public class AutoToolChain extends Check implements PacketReceiveListener {

    private static final int CAPACITY = 64;
    private static final long FORWARD_WINDOW_NANOS = 120_000_000L;
    private static final long RETURN_WINDOW_NANOS = 1_200_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] cycleTimes = new long[CAPACITY];
    private final double[] cycleTurnarounds = new double[CAPACITY];
    private int index;
    private int size;

    private int currentSlot;
    private long digStartNanos;
    private int slotAtDigStart = -1;
    private boolean forwardPending;
    private int forwardSlot = -1;
    private long forwardNanos;
    private long lastFlagNanos;

    private int windowSeconds;
    private int minCycles;
    private double meanTurnaroundMs;

    public AutoToolChain(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = config.getIntElse("inventory-automation.tool-swap-window-seconds", 60);
        minCycles = config.getIntElse("inventory-automation.tool-swap-min-cycles", 6);
        meanTurnaroundMs = config.getDoubleElse("inventory-automation.tool-swap-mean-turnaround-ms", 400.0D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            if (dig.getAction() == DiggingAction.START_DIGGING) {
                digStartNanos = now;
                slotAtDigStart = currentSlot;
            }
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) {
            return;
        }
        int newSlot = new WrapperPlayClientHeldItemChange(event).getSlot();
        int previousSlot = currentSlot;
        currentSlot = newSlot;

        if (newSlot == previousSlot) {
            return;
        }

        if (forwardPending && now - forwardNanos > RETURN_WINDOW_NANOS) {
            forwardPending = false;
        }

        if (forwardPending) {
            if (newSlot == slotAtDigStart && newSlot != forwardSlot) {
                recordCycle(now, (now - forwardNanos) / 1_000_000.0D);
            }
            if (newSlot != forwardSlot) {
                forwardPending = false;
            }
            return;
        }

        if (slotAtDigStart != -1
                && now - digStartNanos <= FORWARD_WINDOW_NANOS
                && newSlot != slotAtDigStart) {
            forwardPending = true;
            forwardSlot = newSlot;
            forwardNanos = now;
        }
    }

    private void recordCycle(long now, double turnaroundMs) {
        cycleTimes[index] = now;
        cycleTurnarounds[index] = turnaroundMs;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        long cutoff = now - windowSeconds * 1_000_000_000L;
        int count = 0;
        double sum = 0.0D;
        for (int k = 0; k < size; k++) {
            int slot = (index - 1 - k + CAPACITY * 2) % CAPACITY;
            if (cycleTimes[slot] <= cutoff) {
                break;
            }
            count++;
            sum += cycleTurnarounds[slot];
        }
        if (count < minCycles) {
            return;
        }
        double mean = sum / count;
        if (mean <= meanTurnaroundMs) {
            flag(String.format(Locale.ROOT, "%d hotbar swap cycles in %ds, mean turnaround %.0fms", count, windowSeconds, mean));
            lastFlagNanos = now;
        }
    }

    public int getWindowCycleCount() {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        for (int k = 0; k < size; k++) {
            if (cycleTimes[(index - 1 - k + CAPACITY * 2) % CAPACITY] <= cutoff) {
                break;
            }
            count++;
        }
        return count;
    }

    public double getWindowMeanTurnaroundMs() {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        double sum = 0.0D;
        for (int k = 0; k < size; k++) {
            int slot = (index - 1 - k + CAPACITY * 2) % CAPACITY;
            if (cycleTimes[slot] <= cutoff) {
                break;
            }
            count++;
            sum += cycleTurnarounds[slot];
        }
        return count == 0 ? 0.0D : sum / count;
    }
}
