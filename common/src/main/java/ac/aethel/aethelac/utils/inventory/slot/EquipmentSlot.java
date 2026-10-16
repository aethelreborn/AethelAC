package ac.aethel.aethelac.utils.inventory.slot;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.inventory.EquipmentType;
import ac.aethel.aethelac.utils.inventory.InventoryStorage;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;

public class EquipmentSlot extends Slot {
    private final EquipmentType type;

    public EquipmentSlot(EquipmentType type, InventoryStorage menu, int slot) {
        super(menu, slot);
        this.type = type;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return type == EquipmentType.getEquipmentSlotForItem(itemStack);
    }

    public boolean mayPickup(AethelPlayer player) {
        ItemStack itemstack = this.getItem();
        return (itemstack.isEmpty() || player.gamemode == GameMode.CREATIVE || itemstack.getEnchantmentLevel(EnchantmentTypes.BINDING_CURSE) == 0) && super.mayPickup(player);
    }
}
