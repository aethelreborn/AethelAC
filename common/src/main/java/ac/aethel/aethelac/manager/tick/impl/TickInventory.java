package ac.aethel.aethelac.manager.tick.impl;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.tick.Tickable;
import ac.aethel.aethelac.player.AethelPlayer;

public class TickInventory implements Tickable {
    @Override
    public void tick() {
        for (AethelPlayer player : AethelAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.inventory.inventory.getInventoryStorage().tickWithBukkit();
        }
    }
}
