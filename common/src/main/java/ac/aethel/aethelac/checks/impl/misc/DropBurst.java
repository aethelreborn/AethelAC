package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects drop-everything macros: dozens of item drops fired within a
 * fraction of a second.
 *
 * <p>DropAll style modules empty the whole inventory in one burst, so the
 * server sees ten or more drop actions inside a single second. Even the
 * fastest human mashing cannot sustain that rate on a single key, and
 * holding the vanilla drop key repeats far slower.</p>
 */
@CheckData(name = "DropBurst", stableKey = "aethel.inventory.drop_burst",
        description = "Burst of item drops inconsistent with human input", experimental = true)
public class DropBurst extends Check implements PacketReceiveListener {
    private static final int CAPACITY = 64;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] dropTimes = new long[CAPACITY];
    private int index;
    private int size;
    private long lastFlagNanos;

    private int windowSeconds;
    private int minCount;

    public DropBurst(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = Math.max(1, config.getIntElse("inventory-automation.drop-window-seconds", 1));
        minCount = Math.max(2, config.getIntElse("inventory-automation.drop-min-count", 12));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }
        DiggingAction action = new WrapperPlayClientPlayerDigging(event).getAction();
        if (action != DiggingAction.DROP_ITEM && action != DiggingAction.DROP_ITEM_STACK) {
            return;
        }

        long now = System.nanoTime();
        dropTimes[index] = now;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        long cutoff = now - windowSeconds * 1_000_000_000L;
        int count = 0;
        for (int k = 0; k < size; k++) {
            if (dropTimes[(index - 1 - k + CAPACITY * 2) % CAPACITY] <= cutoff) {
                break;
            }
            count++;
        }
        if (count >= minCount) {
            flag(String.format(Locale.ROOT, "%d drops in %ds", count, windowSeconds));
            lastFlagNanos = now;
        }
    }
}
