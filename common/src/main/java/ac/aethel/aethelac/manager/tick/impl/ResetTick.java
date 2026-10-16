package ac.aethel.aethelac.manager.tick.impl;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.tick.Tickable;
import ac.aethel.aethelac.player.AethelPlayer;

public class ResetTick implements Tickable {
    @Override
    public void tick() {
        for (AethelPlayer player : AethelAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.packetEntityReplication.tickStartTick();
        }
    }
}
