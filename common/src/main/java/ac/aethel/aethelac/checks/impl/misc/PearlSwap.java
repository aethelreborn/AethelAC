package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects pearl-flashing macros: an ender pearl thrown within milliseconds
 * of selecting it, usually followed by an instant switch back.
 *
 * <p>Prestige's Pearl Catch and the switchback slot selection of aura
 * clients throw a pre-bound pearl in the same tick it is selected; a human
 * pressing a pearl hotkey and clicking lands a tick or more later, and
 * reverting the slot instantly on top of that is another machine
 * motion.</p>
 */
@CheckData(name = "PearlSwap", stableKey = "aethel.misc.pearl_swap",
        description = "Ender pearl thrown within milliseconds of slot change", experimental = true)
public class PearlSwap extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long lastUseNanos;
    private long lastSwapNanos;
    private int currentSlot;
    private int previousSlot = -1;

    private int pairs;
    private int reverts;
    private long firstEventNanos;
    private long lastFlagNanos;

    private double gapMs;
    private int minPairs;
    private double revertMs;
    private double counterSeconds;
    private int maxPingMs;

    public PearlSwap(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        gapMs = config.getDoubleElse("attack-patterns.pearl-use-gap-ms", 15.0D);
        minPairs = Math.max(2, config.getIntElse("attack-patterns.pearl-min-pairs", 4));
        revertMs = config.getDoubleElse("attack-patterns.pearl-revert-ms", 250.0D);
        counterSeconds = config.getDoubleElse("attack-patterns.pearl-counter-seconds", 60.0D);
        maxPingMs = config.getIntElse("attack-patterns.timing-max-ping-ms", 150);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            handleSwap(System.nanoTime(), new WrapperPlayClientHeldItemChange(event).getSlot());
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            if (player.inventory.getHeldItem().getType() == ItemTypes.ENDER_PEARL) {
                handleUse(System.nanoTime());
            }
        }
    }

    private void handleSwap(long now, int slot) {
        if (player.getTransactionPing() > maxPingMs) {
            return;
        }

        if (lastSwapNanos != 0L && now - lastSwapNanos <= (long) (revertMs * 1_000_000L)
                && slot == previousSlot && slot != currentSlot) {
            reverts++;
            recordEvent(now);
        }

        previousSlot = currentSlot;
        currentSlot = slot;
        lastSwapNanos = now;

        if (lastUseNanos != 0L && now - lastUseNanos <= (long) (gapMs * 1_000_000L)) {
            pairs++;
            recordEvent(now);
        }
    }

    private void handleUse(long now) {
        if (player.getTransactionPing() > maxPingMs) {
            return;
        }
        lastUseNanos = now;
        if (lastSwapNanos != 0L && now - lastSwapNanos <= (long) (gapMs * 1_000_000L)) {
            pairs++;
            recordEvent(now);
        }
    }

    private void recordEvent(long now) {
        if (firstEventNanos == 0L || now - firstEventNanos > (long) (counterSeconds * 1_000_000_000L)) {
            pairs = 0;
            reverts = 0;
            firstEventNanos = now;
        }

        if (pairs >= minPairs && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "%d same-tick pearl throws after slot change, %d instant slot reverts in window", pairs, reverts));
            lastFlagNanos = now;
        } else if (reverts >= 3 && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "%d instant slot reverts around pearl throws (switchback macro)", reverts));
            lastFlagNanos = now;
        }
    }
}
