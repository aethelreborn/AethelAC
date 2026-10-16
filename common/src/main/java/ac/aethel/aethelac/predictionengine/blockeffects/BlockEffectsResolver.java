package ac.aethel.aethelac.predictionengine.blockeffects;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.math.Vector3dm;

import java.util.List;

public interface BlockEffectsResolver {

    void applyEffectsFromBlocks(AethelPlayer player, Vector3dm clientVelocity, boolean onlyApplyVelocity, List<AethelPlayer.Movement> movements);

}
