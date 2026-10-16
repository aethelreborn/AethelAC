package ac.aethel.aethelac.checks.impl.multiactions;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

@CheckData(name = "MultiActionsA", stableKey = "aethel.multiactions.attack_while_using", description = "Attacked while using an item", experimental = true)
public class MultiActionsA extends Check implements PreViaPacketReceiveListener {
    public MultiActionsA(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (player.packetStateData.isSlowedByUsingItem() && (player.packetStateData.lastSlotSelected == player.packetStateData.getSlowedByUsingItemSlot() || player.packetStateData.itemInUseHand == InteractionHand.OFF_HAND)) {
            if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY && new WrapperPlayClientInteractEntity(event).getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
                    || event.getPacketType() == PacketType.Play.Client.ATTACK || event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY
                    || event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING && new WrapperPlayClientPlayerDigging(event).getAction() == DiggingAction.STAB) {
                if (flag() && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
