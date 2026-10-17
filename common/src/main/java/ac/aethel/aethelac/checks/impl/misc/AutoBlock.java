package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects auto-block modules: shield use toggled within milliseconds of
 * attacks, over and over.
 *
 * <p>Shield-drain and auto-block modules raise the shield the instant their
 * own attack lands (or drop it the instant the next attack starts) so the
 * opponent can never punish. Pressing the secondary key that fast after -
 * or before - every single click is beyond reliable human coordination,
 * while the module's shield activation rides the same client tick as the
 * attack. The pattern requires a shield actually held at use time, so
 * regular sword spamming and real placements never count.</p>
 */
@CheckData(name = "AutoBlock", stableKey = "aethel.misc.auto_block",
        description = "Toggled shield use within milliseconds of attacks", experimental = true)
public class AutoBlock extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] ring = new long[8];
    private int index;

    private long lastAttackNanos;
    private long lastUseNanos;
    private long lastFlagNanos;

    private double maxGapMs;
    private int minEvents;
    private double counterSeconds;
    private int maxPingMs;

    public AutoBlock(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxGapMs = config.getDoubleElse("attack-patterns.autoblock-max-gap-ms", 50.0D);
        minEvents = Math.max(2, config.getIntElse("attack-patterns.autoblock-min-events", 4));
        counterSeconds = config.getDoubleElse("attack-patterns.autoblock-counter-seconds", 60.0D);
        maxPingMs = config.getIntElse("attack-patterns.timing-max-ping-ms", 150);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                handleAttack(System.nanoTime());
            }
            return;
        }

        boolean use;
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            use = true;
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            use = new WrapperPlayClientPlayerBlockPlacement(event).getFace() == BlockFace.OTHER;
        } else {
            return;
        }
        if (!use) return;

        if (player.inventory.getHeldItem().getType() != ItemTypes.SHIELD) {
            return;
        }
        handleUse(System.nanoTime());
    }

    private void handleAttack(long now) {
        if (player.getTransactionPing() > maxPingMs) {
            return;
        }
        lastAttackNanos = now;
        if (lastUseNanos != 0L && now - lastUseNanos <= (long) (maxGapMs * 1_000_000L)) {
            recordEvent(now);
        }
    }

    private void handleUse(long now) {
        if (player.getTransactionPing() > maxPingMs) {
            return;
        }
        lastUseNanos = now;
        if (lastAttackNanos != 0L && now - lastAttackNanos <= (long) (maxGapMs * 1_000_000L)) {
            recordEvent(now);
        }
    }

    private void recordEvent(long now) {
        ring[index] = now;
        index = (index + 1) % ring.length;

        int events = 0;
        for (long stamp : ring) {
            if (stamp != 0L && now - stamp <= (long) (counterSeconds * 1_000_000_000L)) {
                events++;
            }
        }

        if (events >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "%d shield toggles within %.0f ms of attacks", events, maxGapMs));
            lastFlagNanos = now;
            Arrays.fill(ring, 0L);
        }
    }
}
