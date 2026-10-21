package ac.aethel.aethelac.checks.impl.breaking;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockBreakListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockBreak;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.math.VectorUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;

@CheckData(name = "FarBreak", stableKey = "aethel.breaking.far_break", description = "Breaking blocks too far away", experimental = true)
public class FarBreak extends Check implements BlockBreakListener {
    private static final Verbose V = Verbose.of("distance={f64:%.2f}");

    public FarBreak(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (!player.cameraEntity.isSelf() || player.inVehicle() || blockBreak.action == DiggingAction.CANCELLED_DIGGING)
            return; // falses

        // The dig packet arrives before the movement packet of the same tick,
        // so the eye we reconstruct is one tick old - while falling, that
        // tick is worth real eye height and inflates the measured distance.
        double fallOffset = player.getEyeFallOffset();
        double min = Double.MAX_VALUE;
        for (double d : player.getPossibleEyeHeights()) {
            double eyeY = player.y + d - fallOffset;
            SimpleCollisionBox box = new SimpleCollisionBox(blockBreak.position);
            Vector3dm best = VectorUtils.cutBoxToVector(player.x, eyeY, player.z, box);
            min = Math.min(min, best.distanceSquared(player.x, eyeY, player.z));
        }

        // getPickRange() determines this?
        // With 1.20.5+ the new attribute determines creative mode reach using a modifier
        double maxReach = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (player.packetStateData.didLastMovementIncludePosition || player.canSkipTicks()) {
            double threshold = player.getMovementThreshold();
            maxReach += Math.hypot(threshold, threshold);
        }

        if (min > maxReach * maxReach) {
            double distance = Math.sqrt(min);
            if (flag(V.write(verbose()).f64(distance)) && shouldModifyPackets()) {
                blockBreak.cancel();
            }
        }
    }
}
