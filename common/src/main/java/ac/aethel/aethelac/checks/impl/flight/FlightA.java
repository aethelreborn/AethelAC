package ac.aethel.aethelac.checks.impl.flight;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

// This check catches 100% of cheaters.
public class FlightA extends Check implements PacketReceiveListener {
    public FlightA(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // If the player sends a flying packet, but they aren't flying, then they are cheating.
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType()) && !player.isFlying) {
            flag();
        }
    }
}
