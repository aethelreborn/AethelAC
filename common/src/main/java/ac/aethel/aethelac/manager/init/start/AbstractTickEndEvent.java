package ac.aethel.aethelac.manager.init.start;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.player.AethelPlayer;

// Intended for future events we inject all platforms at the end of a tick
public abstract class AbstractTickEndEvent implements StartableInitable {

    @Override
    public void start() {

    }

    protected void onEndOfTick(AethelPlayer player, boolean flush) {
        player.packetEntityReplication.onEndOfTickEvent(true, flush);
    }

    protected boolean shouldInjectEndTick() {
        return AethelAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("Reach.enable-post-packet", false);
    }
}
