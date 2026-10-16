package ac.aethel.aethelac.checks.impl.badpackets;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;

@CheckData(name = "BadPacketsS", stableKey = "aethel.badpackets.window_confirmation_not_accepted", description = "Sent a window confirmation packet marked as not accepted")
public class BadPacketsS extends Check implements PacketReceiveListener {
    public BadPacketsS(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION
                && !new WrapperPlayClientWindowConfirmation(event).isAccepted()
                && flag() && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
