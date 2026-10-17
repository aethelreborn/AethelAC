package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

/**
 * Optional server policy: cancel attack packets from players whose transaction
 * ping exceeds the configured ceiling so their hits do not register.
 *
 * <p>This module never flags. It exists for servers that want latency-bounded
 * hit registration on top of the detection suite, mirroring the empirical
 * ~150 ms boundary above which combat packet order and timing stop being
 * trustworthy. Disabled by default ({@code -1}).</p>
 */
@CheckData(name = "PingPolicy", stableKey = "aethel.misc.ping_policy",
        description = "Cancels attacks from players over the configured ping ceiling (policy, no flags)")
public class PingPolicy extends Check implements PacketReceiveListener {
    private int ceilingMs;

    public PingPolicy(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        ceilingMs = config.getIntElse("attack-patterns.cancel-attack-over-ping-ms", -1);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (ceilingMs <= 0) {
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY
                && event.getPacketType() != PacketType.Play.Client.ATTACK) {
            return;
        }
        if (!shouldModifyPackets()) {
            return;
        }
        if (player.getTransactionPing() > ceilingMs) {
            event.setCancelled(true);
        }
    }
}
