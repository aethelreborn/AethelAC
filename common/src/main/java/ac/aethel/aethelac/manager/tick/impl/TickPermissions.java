package ac.aethel.aethelac.manager.tick.impl;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.config.BaseConfigManager;
import ac.aethel.aethelac.manager.tick.Tickable;
import ac.aethel.aethelac.player.AethelPlayer;

public class TickPermissions implements Tickable {

    @Override
    public void tick() {
        BaseConfigManager config = AethelAPI.INSTANCE.getConfigManager();
        int interval = config.getUpdatePermissionTicks();
        if (interval <= 0 || AethelAPI.INSTANCE.getTickManager().currentTick % interval != 0) return;

        for (AethelPlayer player : AethelAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.updatePermissions();
        }
    }
}
