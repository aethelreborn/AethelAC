package ac.aethel.aethelac.checks.impl.breaking;

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

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects dig starts delivered faster than the client input loop allows.
 *
 * <p>The vanilla client polls its attack input once per tick and sends at
 * most one dig-start packet per tick, so consecutive starts are physically
 * at least ~50 ms apart - even while sweep-mining, where each block start
 * rides on its own tick. nuker modules flooding start packets for one or
 * more positions deliver several within a single input tick. A client hitch
 * can release a backlog of buffered clicks, so isolated pairs are ignored
 * and only repeated sub-tick delivery within the counter window flags. This
 * complements MultiBreak (same movement tick, different positions) by
 * catching same-position restarts and packet-level floods.</p>
 */
@CheckData(name = "SubTickBreak", stableKey = "aethel.breaking.subtick_break",
        description = "Started multiple digs within a single client input tick", experimental = true)
public class SubTickBreak extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] ring = new long[8];
    private int index;

    private long lastStartNanos;
    private long lastFlagNanos;

    private long maxIntervalNanos;
    private int minEvents;
    private long counterNanos;

    public SubTickBreak(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxIntervalNanos = Math.max(1, config.getIntElse("attack-patterns.subtick-break-max-interval-ms", 25)) * 1_000_000L;
        minEvents = Math.max(2, config.getIntElse("attack-patterns.subtick-break-min-events", 3));
        counterNanos = Math.max(1, config.getIntElse("attack-patterns.subtick-break-counter-seconds", 10)) * 1_000_000_000L;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }

        WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
        if (packet.getAction() != DiggingAction.START_DIGGING) {
            return;
        }

        long now = System.nanoTime();
        long interval = now - lastStartNanos;
        lastStartNanos = now;

        if (interval > maxIntervalNanos) {
            return;
        }

        long measuredInterval = interval;
        ring[index] = now;
        index = (index + 1) % ring.length;

        int events = 0;
        for (long stamp : ring) {
            if (stamp != 0L && now - stamp <= counterNanos) {
                events++;
            }
        }

        if (events >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "%d dig starts delivered within %.0f ms of each other - the client input loop allows at most one per tick",
                    events, measuredInterval / 1.0E6D));
            lastFlagNanos = now;
            Arrays.fill(ring, 0L);
        }
    }
}
