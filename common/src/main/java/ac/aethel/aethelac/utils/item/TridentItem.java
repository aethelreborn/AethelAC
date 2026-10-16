package ac.aethel.aethelac.utils.item;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;

public class TridentItem extends ItemBehaviour {

    public static final TridentItem INSTANCE = new TridentItem();

    @Override
    public boolean canUse(ItemStack item, CompensatedWorld world, AethelPlayer player, InteractionHand hand) {
        if (this.nextDamageWillBreak(item)) {
            return false;
        }

        return item.getEnchantmentLevel(EnchantmentTypes.RIPTIDE) <= 0;
    }

    private boolean nextDamageWillBreak(ItemStack item) {
        return item.isDamageableItem() && item.getDamageValue() >= item.getMaxDamage() - 1;
    }

}
