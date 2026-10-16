package ac.aethel.aethelac.platform.fabric.initables;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.init.start.AbstractTickEndEvent;
import ac.aethel.aethelac.platform.fabric.FabricServerEvents;
import ac.aethel.aethelac.player.AethelPlayer;

public class FabricTickEndEvent extends AbstractTickEndEvent {

    @Override
    public void start() {
        if (!super.shouldInjectEndTick()) {
            return;
        }

        FabricServerEvents.onEndTick(server -> tickAllPlayers());
    }

    private void tickAllPlayers() {
        for (AethelPlayer player : AethelAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (player.disableAethel) continue;
            super.onEndOfTick(player, true);
        }
    }
}
