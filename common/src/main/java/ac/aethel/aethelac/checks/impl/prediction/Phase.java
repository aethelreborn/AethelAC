package ac.aethel.aethelac.checks.impl.prediction;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PostPredictionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.nmsutil.Collisions;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.util.ArrayList;
import java.util.List;

@CheckData(name = "Phase", stableKey = "aethel.prediction.phase", description = "Moved into a solid block during movement prediction", setback = 1, decay = 0.005)
public class Phase extends Check implements PostPredictionListener {
    private SimpleCollisionBox oldBB;

    public Phase(AethelPlayer player) {
        super(player);
        oldBB = player.boundingBox;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!player.getSetbackTeleportUtil().blockOffsets && !predictionComplete.getData().isTeleport() && predictionComplete.isChecked()) { // Not falling through world
            SimpleCollisionBox newBB = player.boundingBox;

            List<SimpleCollisionBox> boxes = new ArrayList<>();
            Collisions.getCollisionBoxes(player, newBB, boxes, false);

            for (SimpleCollisionBox box : boxes) {
                if (newBB.isIntersected(box) && !oldBB.isIntersected(box)) {
                    if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)) {
                        // A bit of a hacky way to get the block state, but this is much faster to use the tuinity method for grabbing collision boxes
                        WrappedBlockState state = player.compensatedWorld.getBlock((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
                        if (BlockTags.ANVIL.contains(state.getType()) || state.getType() == StateTypes.CHEST || state.getType() == StateTypes.TRAPPED_CHEST) {
                            continue; // 1.8 glitchy block, ignore
                        }
                    }
                    flagWithSetback();
                    return;
                }
            }
        }

        oldBB = player.boundingBox;
        reward();
    }
}
