package ac.aethel.aethelac.checks.impl.badpackets;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;

@CheckData(name = "BadPacketsI", stableKey = "aethel.badpackets.spoofed_abilities", description = "Claimed to be flying while unable to fly")
public class BadPacketsI extends Check implements PreViaPacketReceiveListener {
    public BadPacketsI(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_ABILITIES
                && new WrapperPlayClientPlayerAbilities(event).isFlying() && !player.canFly
                && flag() && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
