package ac.aethel.aethelac.utils.item;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;

public class UnsupportedItem extends ItemBehaviour {

    public static final UnsupportedItem INSTANCE = new UnsupportedItem();

    @Override
    public boolean canUse(ItemStack item, CompensatedWorld world, AethelPlayer player, InteractionHand hand) {
        return false;
    }

}
