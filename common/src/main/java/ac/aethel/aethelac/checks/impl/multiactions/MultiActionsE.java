package ac.aethel.aethelac.checks.impl.multiactions;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

@CheckData(name = "MultiActionsE", stableKey = "aethel.multiactions.swing_while_using", description = "Swinging while using an item", experimental = true)
public class MultiActionsE extends Check implements PreViaPacketReceiveListener {
    private boolean dropping;

    public MultiActionsE(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (!dropping && player.packetStateData.isSlowedByUsingItem()
                && (player.packetStateData.lastSlotSelected == player.packetStateData.getSlowedByUsingItemSlot() || player.packetStateData.itemInUseHand == InteractionHand.OFF_HAND)
                && isAnimation(event.getPacketType())) {
            // this is possible to false on 1.7
            if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_7_10)) {
                return;
            }

            if (flag() && shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }

        if (!isAsync(event.getPacketType())) {
            dropping = false;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING
                && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_15)
                && player.getClientVersion().isOlderThan(ClientVersion.V_26_3)) {
            DiggingAction action = new WrapperPlayClientPlayerDigging(event).getAction();
            dropping = action == DiggingAction.DROP_ITEM || action == DiggingAction.DROP_ITEM_STACK;
        }
    }
}
