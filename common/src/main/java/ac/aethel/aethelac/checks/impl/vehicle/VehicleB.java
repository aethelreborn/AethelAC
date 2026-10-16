package ac.aethel.aethelac.checks.impl.vehicle;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "VehicleB", stableKey = "aethel.vehicle.spoofed_vehicle", description = "Claimed to be in a vehicle while not in a vehicle")
public class VehicleB extends Check implements PreViaPacketReceiveListener {
    public VehicleB(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE && !player.inVehicle()
                && flag() && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
