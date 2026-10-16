package ac.aethel.aethelac.manager.init.start;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.player.AethelPlayer;

public class PacketLimiter implements StartableInitable {
    @Override
    public void start() {
        AethelAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(AethelAPI.INSTANCE.getGrimPlugin(), () -> {
            for (AethelPlayer player : AethelAPI.INSTANCE.getPlayerDataManager().getEntries()) {
                // Avoid concurrent reading on an integer as it's results are unknown
                player.cancelledPackets.set(0);
            }
        }, 1, 20);
    }
}
