package ac.aethel.aethelac.checks.impl.crash;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PrePredictionPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "CrashA", stableKey = "aethel.crash.large_position", description = "Sent a position outside the valid world bounds")
public class CrashA extends Check implements PrePredictionPacketReceiveListener {
    private static final double HARD_CODED_BORDER = 2.9999999E7D;

    public CrashA(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPrePredictionPacketReceive(PacketReceiveEvent event) {
        if (player.packetStateData.lastPacketWasTeleport) return;
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);

            if (!packet.hasPositionChanged()) return;
            // Y technically is uncapped, but no player will reach these values legit
            if (Math.abs(packet.getLocation().getX()) > HARD_CODED_BORDER || Math.abs(packet.getLocation().getZ()) > HARD_CODED_BORDER || Math.abs(packet.getLocation().getY()) > Integer.MAX_VALUE) {
                flag(); // Ban
                executeViolationSetback();
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }
    }
}
