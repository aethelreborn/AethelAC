package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects attacks fired faster than the client input loop allows.
 *
 * <p>The vanilla client processes mouse input once per client tick and can
 * emit at most one attack packet per tick, so consecutive attacks are
 * physically at least ~50 ms apart. Attacks arriving within a fraction of
 * that window mean multiple packets per input tick - the signature of
 * packet-flooding kill-aura modules and duplicated attack exploits. A single
 * client hitch can release a backlog of buffered clicks, so isolated pairs
 * are ignored and only repeated sub-tick delivery within the counter window
 * flags.</p>
 */
@CheckData(name = "SubTickClick", stableKey = "aethel.combat.subtick_click",
        description = "Attacked multiple times within a single client input tick", experimental = true)
public class SubTickClick extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] ring = new long[8];
    private int index;

    private long lastAttackNanos;
    private long lastFlagNanos;

    private long maxIntervalNanos;
    private int minEvents;
    private long counterNanos;

    public SubTickClick(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxIntervalNanos = Math.max(1, config.getIntElse("attack-patterns.subtick-click-max-interval-ms", 25)) * 1_000_000L;
        minEvents = Math.max(2, config.getIntElse("attack-patterns.subtick-click-min-events", 3));
        counterNanos = Math.max(1, config.getIntElse("attack-patterns.subtick-click-counter-seconds", 10)) * 1_000_000_000L;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean attack;
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            attack = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        } else if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            attack = true;
        } else {
            return;
        }
        if (!attack) return;

        long now = System.nanoTime();
        long interval = now - lastAttackNanos;
        lastAttackNanos = now;

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
                    "%d attacks delivered within %.0f ms of each other - the client input loop allows at most one per tick",
                    events, measuredInterval / 1.0E6D));
            lastFlagNanos = now;
            Arrays.fill(ring, 0L);
        }
    }
}
