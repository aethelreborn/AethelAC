package ac.aethel.aethelac.manager.tick.impl;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.tick.Tickable;
import ac.aethel.aethelac.player.AethelPlayer;

public class ClearRecentlyUpdatedBlocks implements Tickable {

    private static final int maxTickAge = 2;

    @Override
    public void tick() {
        for (AethelPlayer player : AethelAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.blockHistory.cleanup(AethelAPI.INSTANCE.getTickManager().currentTick - maxTickAge);
        }
    }
}
