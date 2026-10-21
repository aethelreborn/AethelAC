package ac.aethel.aethelac.checks.impl.scaffolding;

import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockPlaceCheck;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.math.VectorUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;

@CheckData(name = "FarPlace", stableKey = "aethel.scaffolding.far_place", description = "Placing blocks from too far away")
public class FarPlace extends BlockPlaceCheck implements BlockPlaceListener {
    public FarPlace(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (!player.cameraEntity.isSelf() || player.inVehicle()) return;

        Vector3i blockPos = place.position;

        if (place.material == StateTypes.SCAFFOLDING) return;

        // The placement packet arrives before the movement packet of the same
        // tick, so the eye we reconstruct is one tick old - while falling,
        // that tick is worth real eye height and inflates the measured
        // distance.
        double fallOffset = player.getEyeFallOffset();
        double min = Double.MAX_VALUE;
        final double[] possibleEyeHeights = player.getPossibleEyeHeights();
        for (double d : possibleEyeHeights) {
            double eyeY = player.y + d - fallOffset;
            SimpleCollisionBox box = new SimpleCollisionBox(blockPos);
            Vector3dm best = VectorUtils.cutBoxToVector(player.x, eyeY, player.z, box);
            min = Math.min(min, best.distanceSquared(player.x, eyeY, player.z));
        }

        // getPickRange() determines this?
        // With 1.20.5+ the new attribute determines creative mode reach using a modifier
        double maxReach = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        double threshold = player.getMovementThreshold();
        maxReach += Math.hypot(threshold, threshold);

        if (min > maxReach * maxReach) { // fail
            if (flag() && shouldModifyPackets() && shouldCancel()) {
                place.resync();
            }
        }
    }
}
