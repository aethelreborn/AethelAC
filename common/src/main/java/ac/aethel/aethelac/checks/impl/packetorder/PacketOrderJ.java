package ac.aethel.aethelac.checks.impl.packetorder;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PostPredictionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "PacketOrderJ", stableKey = "aethel.packetorder.attack_interact_use_order", description = "Sent use item after attacking without the expected interaction packet", experimental = true)
public class PacketOrderJ extends Check implements PacketReceiveListener, PostPredictionListener {
    public PacketOrderJ(final AethelPlayer player) {
        super(player);
    }

    private int invalid;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT || event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            // we don't check stabbing here because you don't need to target an entity to stab
            if (player.packetOrderProcessor.isAttacking() && !player.packetOrderProcessor.isInteracting()) {
                if (!player.canSkipTicks()) {
                    if (flag() && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                } else {
                    invalid++;
                }
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (; invalid >= 1; invalid--) {
                flag();
            }
        }

        invalid = 0;
    }
}
