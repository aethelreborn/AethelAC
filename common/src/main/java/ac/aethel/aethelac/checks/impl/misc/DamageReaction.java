package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects machine-speed reactions to taking damage: using an item or
 * manipulating the offhand within a fraction of human reflex time of every
 * health decrease.
 *
 * <p>ClickPearl and AutoGApple style modules fire the use action the moment
 * their health packet arrives, so the server sees a use item packet
 * milliseconds after its own health update with metronome-like consistency.
 * AutoSwap and AutoTotem modules move the offhand at the same machine speed
 * through offhand clicks or swap actions. Human reaction to damage starts
 * well after 150 ms, so a run of sub-120 ms reactions is not physical.</p>
 */
@CheckData(name = "DamageReaction", stableKey = "aethel.misc.damage_reaction", description = "Used an item at machine speed after taking damage", experimental = true)
public class DamageReaction extends Check implements PacketSendListener, PacketReceiveListener {

    private static final int CAPACITY = 32;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int OFFHAND_SLOT = 45;
    private static final int SWAP_WITH_OFFHAND_BUTTON = 40;

    private final double[] reactionMs = new double[CAPACITY];
    private final long[] reactionTimes = new long[CAPACITY];
    private int index;
    private int size;

    private double lastHealth = Double.NaN;
    private long pendingDamageNanos;
    private long lastFlagNanos;

    private double maxReactionMs;
    private double meanReactionMs;
    private int minPairs;
    private int windowSeconds;

    public DamageReaction(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxReactionMs = config.getDoubleElse("reaction-analysis.damage-reaction-max-ms", 300.0D);
        meanReactionMs = config.getDoubleElse("reaction-analysis.damage-reaction-mean-ms", 120.0D);
        minPairs = config.getIntElse("reaction-analysis.damage-reaction-min-pairs", 6);
        windowSeconds = config.getIntElse("reaction-analysis.damage-reaction-window-seconds", 300);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.UPDATE_HEALTH) {
            return;
        }
        WrapperPlayServerUpdateHealth health = new WrapperPlayServerUpdateHealth(event);
        float current = health.getHealth();
        if (!Double.isNaN(lastHealth) && current < lastHealth) {
            pendingDamageNanos = System.nanoTime();
        }
        lastHealth = current;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean reaction = false;
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM
                || event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            reaction = true;
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            reaction = new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND;
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            reaction = click.getSlot() == OFFHAND_SLOT || click.getButton() == SWAP_WITH_OFFHAND_BUTTON;
        }
        if (!reaction || pendingDamageNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        double deltaMs = (now - pendingDamageNanos) / 1_000_000.0D;
        pendingDamageNanos = 0L;
        if (deltaMs > maxReactionMs) {
            return;
        }

        reactionMs[index] = deltaMs;
        reactionTimes[index] = now;
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
            if (reactionTimes[slot] <= cutoff) {
                break;
            }
            count++;
            sum += reactionMs[slot];
        }
        if (count < minPairs) {
            return;
        }
        double mean = sum / count;
        if (mean <= meanReactionMs) {
            flag(String.format(Locale.ROOT, "%d damage reactions in %ds, mean %.0fms", count, windowSeconds, mean));
            lastFlagNanos = now;
        }
    }

    public int getWindowPairCount() {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        for (int k = 0; k < size; k++) {
            if (reactionTimes[(index - 1 - k + CAPACITY * 2) % CAPACITY] <= cutoff) {
                break;
            }
            count++;
        }
        return count;
    }

    public double getWindowMeanReactionMs() {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        double sum = 0.0D;
        for (int k = 0; k < size; k++) {
            int slot = (index - 1 - k + CAPACITY * 2) % CAPACITY;
            if (reactionTimes[slot] <= cutoff) {
                break;
            }
            count++;
            sum += reactionMs[slot];
        }
        return count == 0 ? 0.0D : sum / count;
    }
}
