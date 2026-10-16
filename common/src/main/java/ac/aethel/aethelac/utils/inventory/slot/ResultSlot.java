package ac.aethel.aethelac.utils.inventory.slot;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.inventory.InventoryStorage;
import com.github.retrooper.packetevents.protocol.item.ItemStack;

public class ResultSlot extends Slot {

    public ResultSlot(InventoryStorage container, int slot) {
        super(container, slot);
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public void onTake(AethelPlayer player, ItemStack itemStack) {
        // Resync the player's inventory
    }
}
