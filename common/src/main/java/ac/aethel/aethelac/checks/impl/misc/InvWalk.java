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
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects interactions while a container is open (invwalk / loot aura).
 *
 * <p>A vanilla client's open container GUI captures mouse input completely:
 * no attack, block placement or digging packet can be produced while the
 * window is up. Cheat modules attacking and looting simultaneously leave the
 * container open for seconds, so any world interaction in that state is
 * impossible - provided the client has had time to see the opened window and
 * never closed it.</p>
 */
@CheckData(name = "InvWalk", stableKey = "aethel.misc.inv_walk",
        description = "Interacted while a container is open", experimental = true)
public class InvWalk extends Check implements PacketReceiveListener, PacketSendListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long containerOpenNanos;
    private int events;
    private long firstEventNanos;
    private long lastFlagNanos;

    private long minOpenNanos;
    private int minEvents;
    private long counterNanos;

    public InvWalk(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minOpenNanos = Math.max(50, config.getIntElse("inventory-automation.invwalk-min-open-ms", 350)) * 1_000_000L;
        minEvents = Math.max(2, config.getIntElse("inventory-automation.invwalk-min-events", 2));
        counterNanos = Math.max(1, config.getIntElse("inventory-automation.invwalk-counter-seconds", 10)) * 1_000_000_000L;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            containerOpenNanos = System.nanoTime();
        } else if (event.getPacketType() == PacketType.Play.Server.CLOSE_WINDOW) {
            containerOpenNanos = 0L;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            containerOpenNanos = 0L;
            return;
        }

        if (containerOpenNanos == 0L) {
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY
                && event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                && event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }

        long now = System.nanoTime();
        long requiredOpenNanos = Math.max(minOpenNanos, player.getTransactionPing() * 2L * 1_000_000L + 100_000_000L);
        if (now - containerOpenNanos < requiredOpenNanos) {
            return;
        }

        if (firstEventNanos == 0L || now - firstEventNanos > counterNanos) {
            events = 0;
            firstEventNanos = now;
        }
        events++;

        if (events >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "interacted with the world %.1f s after a container opened",
                    (now - containerOpenNanos) / 1_000_000_000.0D));
            lastFlagNanos = now;
            events = 0;
            firstEventNanos = 0L;
        }
    }
}
