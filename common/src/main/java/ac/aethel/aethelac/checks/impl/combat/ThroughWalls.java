package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.collisions.HitboxData;
import ac.aethel.aethelac.utils.collisions.datatypes.CollisionBox;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.data.HitData;
import ac.aethel.aethelac.utils.data.Pair;
import ac.aethel.aethelac.utils.data.packetentity.PacketEntity;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.nmsutil.ReachUtils;
import ac.aethel.aethelac.utils.nmsutil.WorldRayTrace;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla cannot attack or interact with an entity that a solid block occludes: the
 * client raycasts the blocks first and only considers entities nearer than the first
 * block hit, so {@code EntityRenderer.getMouseOver} never returns an entity behind a
 * wall. An interaction packet whose aimed ray is blocked before it reaches the target
 * is therefore impossible for an unmodified client.
 *
 * <p>The rotation is reconstructed from the packet's own moment, not from the next
 * movement packet. A click uses the previous render frame's pick, and the server
 * already holds the rotation that frame was rendered with: 1.8
 * ({@code clickMouse()} before {@code theWorld.updateEntities()}) and 1.21
 * ({@code handleInputEvents()} before {@code world.tickEntities()}) both queue the
 * interaction before the tick that reports the new rotation. The held rotation is
 * therefore the aimed one, up to the render interpolation between the last two ticks;
 * the check only runs when that change is small enough to make the difference
 * irrelevant.
 *
 * <p>Further sources of legitimate divergence are compensated for:
 * <ul>
 *   <li>A block placed by another player reaches this client after one network trip.
 *       Blocks changed within the last round trip are ignored.</li>
 *   <li>A rejected client-side break may leave the client seeing air where the
 *       server still has the block. Attacks within a second of a break are
 *       ignored; placements are not, since a predicted placement can only make
 *       the client see more than the server.</li>
 *   <li>The target box is the interpolation-aware box at interaction time, expanded
 *       past the client's targeting margin, and the eye is the highest possible
 *       height, so pose and interpolation changes can only hide an action, never
 *       invent one.</li>
 * </ul>
 */
@CheckData(name = "ThroughWalls", stableKey = "aethel.combat.through_walls",
        description = "Attacked or interacted with a player through a solid block", setback = 0, decay = 0.01)
public class ThroughWalls extends Check implements PacketReceiveListener {
    // Grace period after this player's own block break. A rejected client-side
    // break prediction can leave the client seeing air where the server still
    // has the block; that window is one round trip, covered with margin here.
    // Placements are deliberately not included: a predicted placement makes the
    // client see more than the server, which can only hide an action.
    private static final long QUIET_BLOCK_BREAK_MS = 1_000L;
    // Minimum age of the blocking block before its presence is trusted.
    private static final long MIN_BLOCK_AGE_MS = 1_000L;
    // The client's pick expands entity boxes by 0.1 for its targeting margin, and
    // interpolation can add a little more. Expanding past both makes a visible
    // sliver win the benefit of the doubt.
    private static final double BOX_MARGIN = 0.15;
    // Largest change between the last two rotations for which render interpolation
    // cannot move the pick by a meaningful angle. A faster turn is skipped: the
    // next movement packet would be needed to know the aimed angle, and using it
    // would judge the click with a rotation the client had not aimed with yet.
    private static final double STABLE_ROTATION_DEGREES = 6.0;

    public ThroughWalls(AethelPlayer player) {
        super(player);
    }

    @Override
    public boolean isApplicable() {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY)) return;

        final WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        // The crosshair pick gates every entity action, not only attacks: a right-click
        // on an entity is only sent once the ray has selected that entity.
        final WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();
        if (action != WrapperPlayClientInteractEntity.InteractAction.ATTACK
                && action != WrapperPlayClientInteractEntity.InteractAction.INTERACT
                && action != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
            return;
        }

        if (player.gamemode == GameMode.SPECTATOR || player.inVehicle() || !player.cameraEntity.isSelf()) return;

        // The click used the previous frame's pick, which is the rotation the server
        // holds right now. If the last two ticks differ by more than a few degrees,
        // the frame's interpolation is farther from that rotation than the target's
        // angular size, so the ray is not usable.
        if (Math.abs(player.yaw - player.lastYaw) > STABLE_ROTATION_DEGREES
                || Math.abs(player.pitch - player.lastPitch) > STABLE_ROTATION_DEGREES) {
            return;
        }

        // A rejected client-side break can leave the client seeing air where the
        // server still has the block; such a hit is not a wall cheat.
        final long now = System.currentTimeMillis();
        if (now - player.lastBlockBreak < QUIET_BLOCK_BREAK_MS) {
            return;
        }

        // While the world's recent-change history has overflowed we cannot tell a
        // fresh wall from an old one; stay silent for that window.
        if (player.compensatedWorld.isBlockHistoryUncertain()) {
            return;
        }

        final PacketEntity entity = player.compensatedEntities.getEntity(packet.getEntityId());
        if (entity == null || packet.getEntityId() == player.entityID || !EntityTypes.PLAYER.equals(entity.getType())) return;

        final double range = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        final Vector3dm look = ReachUtils.getLook(player, player.yaw, player.pitch);

        // The client's eye is whichever pose it was in. Standing is the highest height,
        // so using it can only make the ray see more, never less: false negatives only.
        double eyeHeight = 0;
        for (double possible : player.getPossibleEyeHeights()) {
            eyeHeight = Math.max(eyeHeight, possible);
        }

        final Vector3d eye = new Vector3d(player.x, player.y + eyeHeight, player.z);
        final Vector3d end = new Vector3d(eye.x + look.getX() * range,
                eye.y + look.getY() * range, eye.z + look.getZ() * range);

        // If the held rotation does not even cover the lag-compensated box, the action
        // fails a different invariant and belongs to Hitboxes, not here.
        final SimpleCollisionBox box = entity.getPossibleCollisionBoxes().copy().expand(BOX_MARGIN);
        final Pair<Vector3d, BlockFace> entityHit = ReachUtils.calculateIntercept(box, eye, end);
        if (entityHit.first() == null) return;
        final double entityDistance = Vector3dm.from(entityHit.first()).distanceSquared(eye.x, eye.y, eye.z);

        final HitData blockHit = traceBlock(eye, end);
        if (blockHit == null) return;

        final double blockDistance = blockHit.blockHitLocation().distanceSquared(eye.x, eye.y, eye.z);
        // An eye inside a block is a different problem; it does not make this a wall hit.
        if (blockDistance < 0.01) return;
        // The block has to sit between the eye and the target, not behind it.
        if (blockDistance >= entityDistance) return;

        final Vector3i position = blockHit.position();
        final long age = player.compensatedWorld.getBlockAgeMillis(position.getX(), position.getY(), position.getZ());
        // A block placed less than one round trip ago may not have reached this client
        // yet, in which case it can legitimately attack through where the wall now is.
        if (age < MIN_BLOCK_AGE_MS + player.getTransactionPing()) return;

        flag("block=" + position.getX() + "," + position.getY() + "," + position.getZ()
                + " age=" + age + "ms distance=" + Math.sqrt(blockDistance));
    }

    private HitData traceBlock(Vector3d start, Vector3d end) {
        final StateType heldItem = player.inventory.getHeldItem().getType().getPlacedType();
        return WorldRayTrace.traverseBlocks(player, start, end, (block, position) -> {
            final CollisionBox data = HitboxData.getBlockHitbox(player, heldItem, player.getClientVersion(),
                    block, false, position.getX(), position.getY(), position.getZ());
            final List<SimpleCollisionBox> boxes = new ArrayList<>();
            data.downCast(boxes);

            double best = Double.MAX_VALUE;
            Vector3d bestHit = null;
            BlockFace bestFace = null;
            for (SimpleCollisionBox candidate : boxes) {
                final Pair<Vector3d, BlockFace> intercept = ReachUtils.calculateIntercept(candidate, start, end);
                if (intercept.first() == null) continue;

                final double distance = Vector3dm.from(intercept.first()).distanceSquared(start.x, start.y, start.z);
                if (distance < best) {
                    best = distance;
                    bestHit = intercept.first();
                    bestFace = intercept.second();
                }
            }

            if (bestHit == null) return null;
            return new HitData(position, Vector3dm.from(bestHit), bestFace, block);
        });
    }
}
