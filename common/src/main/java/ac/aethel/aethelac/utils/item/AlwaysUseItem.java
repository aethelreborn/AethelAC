package ac.aethel.aethelac.utils.item;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.latency.CompensatedWorld;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;

public class AlwaysUseItem extends ItemBehaviour {

    public static final AlwaysUseItem INSTANCE = new AlwaysUseItem();

    @Override
    public boolean canUse(ItemStack item, CompensatedWorld world, AethelPlayer player, InteractionHand hand) {
        return true;
    }

}
