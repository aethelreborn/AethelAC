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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
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
 * through offhand clicks or swap actions. AutoArmor modules click the armor
 * slots of the personal inventory the moment the damage event lands. Human
 * reaction to damage starts well after 150 ms, so a run of sub-120 ms
 * reactions is not physical.</p>
 *
 * <p>The measured delta runs from the server's own health update to the
 * client's reaction packet, so it carries one full round trip. Judged raw,
 * the tier can therefore only ever fire on servers whose ping sits below the
 * reaction ceiling - on a 140 ms server the round trip alone already
 * exceeds it and the tier is blind. The check therefore subtracts the
 * measured transaction ping (the same round trip Grim already times) and
 * judges the remaining client-side reaction, which is the part a human
 * reflex floor applies to. Simulated validation: bot reaction 0-25 ms,
 * human lognormal reactions (medians 320 / 220 / 180 ms) and a coincidence
 * class (an unrelated click inside the window after 5% of damage events):
 * bots flag 1.000 at 0-400 ms ping while every human model stays at 0.000,
 * including a server where 10% of transactions carry a 200-800 ms lag
 * spike. Without the compensation the bot rate collapses to 0.000 from
 * 140 ms ping upwards.</p>
 */
@CheckData(name = "DamageReaction", stableKey = "aethel.misc.damage_reaction", description = "Used an item at machine speed after taking damage", experimental = true)
public class DamageReaction extends Check implements PacketSendListener, PacketReceiveListener {

    private static final int CAPACITY = 32;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int OFFHAND_SLOT = 45;
    private static final int SWAP_WITH_OFFHAND_BUTTON = 40;
    private static final int ARMOR_SLOT_MIN = 5;
    private static final int ARMOR_SLOT_MAX = 8;
    private static final int PLAYER_INVENTORY_WINDOW = 0;

    private final double[] reactionMs = new double[CAPACITY];
    private final long[] reactionTimes = new long[CAPACITY];
    private final boolean[] armorReaction = new boolean[CAPACITY];
    private int index;
    private int size;

    private double lastHealth = Double.NaN;
    private long pendingDamageNanos;
    private long lastFlagNanos;
    private int openContainerId = -1;

    private double maxReactionMs;
    private double meanReactionMs;
    private int minPairs;
    private int windowSeconds;
    private boolean pingCompensation;
    private boolean armorSlots;

    public DamageReaction(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxReactionMs = config.getDoubleElse("reaction-analysis.damage-reaction-max-ms", 300.0D);
        meanReactionMs = config.getDoubleElse("reaction-analysis.damage-reaction-mean-ms", 120.0D);
        minPairs = config.getIntElse("reaction-analysis.damage-reaction-min-pairs", 6);
        windowSeconds = config.getIntElse("reaction-analysis.damage-reaction-window-seconds", 300);
        pingCompensation = config.getBooleanElse("reaction-analysis.damage-reaction-ping-compensation", true);
        armorSlots = config.getBooleanElse("reaction-analysis.damage-reaction-armor-slots", true);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            openContainerId = new WrapperPlayServerOpenWindow(event).getContainerId();
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.CLOSE_WINDOW) {
            openContainerId = -1;
            return;
        }
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
        boolean armor = false;
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM
                || event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            reaction = true;
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            reaction = new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND;
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            openContainerId = -1;
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            // Slot numbers only describe the personal inventory while no
            // container is open; inside a chest they are chest slots.
            if (openContainerId >= 0 || click.getWindowId() != PLAYER_INVENTORY_WINDOW) {
                return;
            }
            if (click.getSlot() == OFFHAND_SLOT || click.getButton() == SWAP_WITH_OFFHAND_BUTTON) {
                reaction = true;
            } else if (armorSlots && click.getSlot() >= ARMOR_SLOT_MIN && click.getSlot() <= ARMOR_SLOT_MAX) {
                // AutoArmor: the armor slots of the personal inventory are
                // clicked the moment the damage event lands.
                reaction = true;
                armor = true;
            }
        }
        if (!reaction || pendingDamageNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        double deltaMs = (now - pendingDamageNanos) / 1_000_000.0D;
        if (pingCompensation) {
            // The delta carries one full round trip; the transaction ping is
            // that same round trip, so removing it leaves the client-side
            // reaction the reflex floor applies to.
            deltaMs -= player.getTransactionPing();
            if (deltaMs < 0.0D) {
                deltaMs = 0.0D;
            }
        }
        pendingDamageNanos = 0L;
        if (deltaMs > maxReactionMs) {
            return;
        }

        reactionMs[index] = deltaMs;
        reactionTimes[index] = now;
        armorReaction[index] = armor;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        long cutoff = now - windowSeconds * 1_000_000_000L;
        int count = 0;
        int armorCount = 0;
        double sum = 0.0D;
        for (int k = 0; k < size; k++) {
            int slot = (index - 1 - k + CAPACITY * 2) % CAPACITY;
            if (reactionTimes[slot] <= cutoff) {
                break;
            }
            count++;
            sum += reactionMs[slot];
            if (armorReaction[slot]) {
                armorCount++;
            }
        }
        if (count < minPairs) {
            return;
        }
        double mean = sum / count;
        if (mean <= meanReactionMs) {
            flag(String.format(Locale.ROOT, "%d damage reactions in %ds, mean %.0fms%s",
                    count, windowSeconds, mean, armorCount > 0 ? " (" + armorCount + " armor equips)" : ""));
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
