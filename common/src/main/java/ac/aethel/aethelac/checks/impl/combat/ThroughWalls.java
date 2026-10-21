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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla cannot attack or interact with an entity that a solid block occludes: the
 * client raycasts the blocks first and only considers entities nearer than the first
 * block hit, so {@code EntityRenderer.getMouseOver} never returns an entity behind a
 * wall. An interaction packet whose aimed ray is blocked before it reaches the target
 * is therefore impossible for an unmodified client.
 *
 * <p>The verdict is deferred by one packet. Both 1.8 ({@code clickMouse()} before
 * {@code theWorld.updateEntities()}) and 1.21 ({@code handleInputEvents()} before
 * {@code world.tickEntities()}) queue the interaction before the tick's movement packet,
 * so at interaction time the server still holds the previous rotation. Waiting for the
 * movement packet that follows the interaction gives the exact angle the client aimed
 * with; evaluating immediately would have to guess between two ticks.
 *
 * <p>Three further sources of legitimate divergence are compensated for:
 * <ul>
 *   <li>A block placed by another player reaches this client after one network trip.
 *       Blocks changed within the last round trip are ignored.</li>
 *   <li>A rejected client-side break may leave the client seeing air where the
 *       server still has the block. Attacks within a second of a break are
 *       ignored; placements are not, since a predicted placement can only make
 *       the client see more than the server.</li>
 *   <li>The target box is the interpolation-aware box captured at attack time, expanded
 *       by a small margin, and the eye is the highest possible height, so pose and
 *       interpolation changes can only hide an attack, never invent one.</li>
 * </ul>
 */
@CheckData(name = "ThroughWalls", stableKey = "aethel.combat.through_walls",
        description = "Attacked or interacted with a player through a solid block", setback = 0, decay = 0.01)
public class ThroughWalls extends Check implements PacketReceiveListener {
    // Grace period after this player's own block break. A rejected client-side
    // break prediction can leave the client seeing air where the server still
    // has the block; that window is one round trip, covered with margin here.
    // Placements are deliberately not included: a predicted placement makes the
    // client see more than the server, which can only hide an attack.
    private static final long QUIET_BLOCK_BREAK_MS = 1_000L;
    // Minimum age of the blocking block before its presence is trusted.
    private static final long MIN_BLOCK_AGE_MS = 1_000L;
    // The client's pick expands entity boxes by 0.1 for its targeting margin, and
    // interpolation can add a little more. Expanding past both makes a visible
    // sliver win the benefit of the doubt.
    private static final double BOX_MARGIN = 0.15;
    // The next movement packet normally arrives in the same tick. Beyond a few ticks
    // the stored rotation is no longer the attack's, so the attack is dropped instead.
    private static final long PENDING_TTL_MS = 250L;
    private static final int MAX_PENDING = 8;

    private record PendingAction(SimpleCollisionBox box, double eyeX, double eyeY, double eyeZ, long capturedAt) {
    }

    private final ArrayDeque<PendingAction> pending = new ArrayDeque<>(2);

    public ThroughWalls(AethelPlayer player) {
        super(player);
    }

    @Override
    public boolean isApplicable() {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_8);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType().equals(PacketType.Play.Client.INTERACT_ENTITY)) {
            capture(event);
            return;
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            evaluatePending(System.currentTimeMillis());
        }
    }

    private void capture(PacketReceiveEvent event) {
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

        // A rejected client-side break can leave the client seeing air where the
        // server still has the block; such a hit is not a wall cheat.
        final long now = System.currentTimeMillis();
        if (now - player.lastBlockBreak < QUIET_BLOCK_BREAK_MS) {
            return;
        }

        final PacketEntity entity = player.compensatedEntities.getEntity(packet.getEntityId());
        if (entity == null || packet.getEntityId() == player.entityID || !EntityTypes.PLAYER.equals(entity.getType())) return;

        // The client's eye is whichever pose it was in. Standing is the highest height,
        // so using it can only make the ray see more, never less: false negatives only.
        double eyeHeight = 0;
        for (double possible : player.getPossibleEyeHeights()) {
            eyeHeight = Math.max(eyeHeight, possible);
        }

        pending.addLast(new PendingAction(entity.getPossibleCollisionBoxes().copy(),
                player.x, player.y + eyeHeight, player.z, now));
        while (pending.size() > MAX_PENDING) {
            pending.removeFirst();
        }
    }

    private void evaluatePending(long now) {
        PendingAction action;
        while ((action = pending.pollFirst()) != null) {
            if (now - action.capturedAt() > PENDING_TTL_MS) continue;
            evaluate(action, now);
        }
    }

    private void evaluate(PendingAction action, long now) {
        // While the world's recent-change history has overflowed we cannot tell a
        // fresh wall from an old one; stay silent for that window.
        if (player.compensatedWorld.isBlockHistoryUncertain()) {
            return;
        }

        // A break sent between the attack and this movement packet can also
        // leave the two world copies disagreeing.
        if (now - player.lastBlockBreak < QUIET_BLOCK_BREAK_MS) {
            return;
        }

        final double range = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        final Vector3dm look = ReachUtils.getLook(player, player.yaw, player.pitch);
        final Vector3d eye = new Vector3d(attack.eyeX(), attack.eyeY(), attack.eyeZ());
        final Vector3d end = new Vector3d(eye.x + look.getX() * range,
                eye.y + look.getY() * range, eye.z + look.getZ() * range);

        // If the attack's own rotation does not cover the lag-compensated box, the
        // attack fails a different invariant and belongs to Hitboxes, not here.
        final SimpleCollisionBox box = attack.box().expand(BOX_MARGIN);
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
