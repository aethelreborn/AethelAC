package ac.aethel.aethelac.utils.blockplace;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;

public interface BlockPlaceFactory {
    void applyBlockPlaceToWorld(AethelPlayer player, BlockPlace place);
}
