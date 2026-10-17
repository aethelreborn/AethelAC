package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects silent-hotbar meta swaps: weapon/attribute/mace/lunge swap macros
 * that change the held slot in the same tick as the attack.
 *
 * <p>Meteor-style auras default to a zero switch delay and LiquidBounce's
 * AutoWeapon syncs the selected slot in the same tick as the attack; the
 * lunge-swap and breach-swap macros do the same from hardware. A human
 * pressing a swap key cannot land the slot change within milliseconds of the
 * click repeatedly, and usually cannot revert the slot instantly either.</p>
 */
@CheckData(name = "SwapAttack", stableKey = "aethel.misc.swap_attack",
        description = "Held slot changed within milliseconds of attacks", experimental = true)
public class SwapAttack extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long lastAttackNanos;
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

    public SwapAttack(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        gapMs = config.getDoubleElse("attack-patterns.swap-attack-gap-ms", 15.0D);
        minPairs = Math.max(2, config.getIntElse("attack-patterns.swap-attack-min-pairs", 4));
        revertMs = config.getDoubleElse("attack-patterns.swap-attack-revert-ms", 250.0D);
        counterSeconds = config.getDoubleElse("attack-patterns.swap-attack-counter-seconds", 60.0D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            handleSwap(System.nanoTime(), new WrapperPlayClientHeldItemChange(event).getSlot());
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                handleAttack(System.nanoTime());
            }
        }
    }

    private void handleSwap(long now, int slot) {
        if (lastSwapNanos != 0L && now - lastSwapNanos <= (long) (revertMs * 1_000_000L)
                && slot == previousSlot && slot != currentSlot) {
            reverts++;
            recordEvent(now);
        }

        previousSlot = currentSlot;
        currentSlot = slot;
        lastSwapNanos = now;

        if (lastAttackNanos != 0L && now - lastAttackNanos <= (long) (gapMs * 1_000_000L)) {
            pairs++;
            recordEvent(now);
        }
    }

    private void handleAttack(long now) {
        lastAttackNanos = now;
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
                    "%d same-tick swap-attack pairs, %d instant slot reverts in window", pairs, reverts));
            lastFlagNanos = now;
        } else if (reverts >= 3 && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "%d instant slot reverts around attacks (swap-back macro)", reverts));
            lastFlagNanos = now;
        }
    }
}
