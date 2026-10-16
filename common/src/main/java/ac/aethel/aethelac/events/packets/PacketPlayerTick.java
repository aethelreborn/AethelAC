package ac.aethel.aethelac.events.packets;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

public class PacketPlayerTick extends PacketListenerAbstract {

    public PacketPlayerTick() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public boolean isPreVia() {
        return true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            AethelPlayer player = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null || player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2))
                return;

            PacketWorldBorder border = player.checkManager.get(PacketWorldBorder.class);
            border.tickBorder();
        } else if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            AethelPlayer player = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2))
                return;

            PacketWorldBorder border = player.checkManager.get(PacketWorldBorder.class);
            border.tickBorder();
        }
    }

}
