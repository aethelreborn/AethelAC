package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import ac.aethel.aethelac.utils.collisions.HitboxData;
import ac.aethel.aethelac.utils.collisions.datatypes.CollisionBox;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.data.HitData;
import ac.aethel.aethelac.utils.data.Pair;
import ac.aethel.aethelac.utils.data.packetentity.PacketEntity;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.nmsutil.ReachUtils;
import ac.aethel.aethelac.utils.nmsutil.WorldRayTrace;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Block placement is gated by the same pick. 1.8's {@code rightClickMouse()} sends
 * the position, side and hit vector of {@code objectMouseOver}, and 1.21's
 * {@code doItemUse()} sends the {@code BlockHitResult} from
 * {@code updateCrosshairTarget()}; both only exist when the block is the one the
 * crosshair actually selected. A placement whose hit point is hidden behind a solid
 * block is therefore impossible. The check converts the packet's block-relative cursor
 * to an absolute point and samples a small precision grid around it (1.8 encodes the
 * cursor in 1/16-block steps). The placement is accepted if any sample is visible from
 * any possible eye position and height; only a fully occluded cursor flags. The face
 * matters too: a visible support block with its hidden back face would put the new
 * block on the far side of the wall, so a sample only counts when the clicked face is
 * the first face of the support that the eye ray meets. Chests,
 * barriers, lecterns and scaffolding are not treated as occluders because their
 * target outline can differ from their collision shape, and non-solid blocks
 * (torches, vines, fire, rails, pressure plates) are ignored: 1.8's pick uses
 * collision boxes, and remaining conservative on modern clients is preferable to
 * charging a player who placed a block past a torch.
 *
 * <p>Further sources of legitimate divergence are compensated for:
 * <ul>
 *   <li>A block placed by another player reaches this client after one network trip.
 *       Blocks changed within the last round trip are ignored.</li>
 *   <li>A rejected client-side break may leave the client seeing air where the
 *       server still has the block. Actions within a second of a break are ignored.</li>
 *   <li>The target box is the interpolation-aware box at interaction time, expanded
 *       past the client's targeting margin, and the eye is the highest possible
 *       height, so pose and interpolation changes can only hide an action, never
 *       invent one.</li>
 * </ul>
 */
@CheckData(name = "ThroughWalls", stableKey = "aethel.combat.through_walls",
        description = "Attacked, interacted with, or placed against a block through a solid block",
        setback = 0, decay = 0.01)
public class ThroughWalls extends Check implements PacketReceiveListener, BlockPlaceListener {
    // Grace period after this player's own block break. A rejected client-side
    // break prediction can leave the client seeing air where the server still
    // has the block; that window is one round trip, covered with margin here.
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

    // The client's cursor is quantised to 1/16 block on 1.8, so the decoded
    // point is within half a cell (+/- 1/32) of the actual click; a 5x5 grid
    // covers that cell. Modern clients send a float cursor, so a 3x3 grid
    // around the point is enough for its rounding.
    private static final double LEGACY_CURSOR_TOLERANCE = 1.0 / 32.0;
    private static final double MODERN_CURSOR_TOLERANCE = 0.005;
    // Reach is measured to the clicked point, with a little room for the block
    // boundary and the attribute rounding.
    private static final double PLACEMENT_RANGE_MARGIN = 0.5;
    // Extend the probe just past the clicked surface so the support block is
    // included in the trace rather than being treated as the endpoint.
    private static final double SAMPLE_EXTENSION = 0.001;
    // An eye inside a block is a different problem; do not turn it into a wall.
    private static final double MIN_OCCLUDER_HIT_DISTANCE_SQ = 0.01;
    // A support placed in this window may still be the old block (or air) in
    // compensatedWorld; the server applies the placement after the tick.
    private static final long RECENT_OWN_PLACEMENT_MS = 1_000L;
    private static final double FACE_EDGE_EPSILON = 0.002;

    private final Map<Vector3i, Long> recentPlacements = new HashMap<>();
    // Set by onReload, which Check calls during construction. Do not give this a
    // field initializer: it would overwrite the config value after super() returns.
    private boolean checkPlacements;

    public ThroughWalls(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        super.onReload(config);
        this.checkPlacements = config.getBooleanElse("ThroughWalls.check-placements", true);
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

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (!checkPlacements || !place.isBlock) return;
        if (place.getFace() == null || place.getFace() == BlockFace.OTHER) return;
        if (place.material == StateTypes.SCAFFOLDING || place.cursor == null) return;
        if (player.gamemode == GameMode.SPECTATOR || player.inVehicle() || !player.cameraEntity.isSelf()) return;

        final long now = System.currentTimeMillis();
        pruneRecentPlacements(now);

        if (place.isCancelled()) return;

        final Vector3i support = place.position;

        // A support placed by this player in the last second may still be the old
        // block (or air) in compensatedWorld; the server applies the placement after
        // the tick. Skip the comparison rather than judge a different shape.
        final Long ownSupportAt = recentPlacements.get(support);
        if (ownSupportAt != null && now - ownSupportAt < RECENT_OWN_PLACEMENT_MS) return;

        // Track the placement before any early return. The client predicts it
        // immediately, so a later placement against the predicted block must not be
        // judged against the older server-side shape (or air) at that position.
        recentPlacements.put(place.getPlacedBlockPos(), now);

        final WrappedBlockState state = player.compensatedWorld.getBlock(support);
        if (state.getType().isAir() || state.getType() == StateTypes.SCAFFOLDING) return;
        // Extended outline shapes (lecterns, shelves, ...) can report a cursor above
        // 1.0; the absolute point is then outside the block cube and the face normal
        // no longer describes the clicked surface. Leave those to FabricatedPlace.
        if (state.getType().exceedsCube() || state.getType() == StateTypes.LECTERN) return;

        if (player.compensatedWorld.isBlockHistoryUncertain()) return;
        if (now - player.lastBlockBreak < QUIET_BLOCK_BREAK_MS) return;

        final long supportAge = player.compensatedWorld.getBlockAgeMillis(
                support.getX(), support.getY(), support.getZ());
        if (supportAge < MIN_BLOCK_AGE_MS + player.getTransactionPing()) return;

        // Fast path: the server's own pick already selects the named support
        // face. Comparing the face as well as the position matters: a packet can
        // name a visible support block but the hidden back face, which would put
        // the new block on the far side of the wall.
        if (place.hitData != null && place.hitData.position().equals(support)
                && place.hitData.closestDirection() == place.getFace()) {
            return;
        }

        final double[] eyeHeights = eyeHeights();
        double range = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (range <= 0) {
            range = player.getClientVersion().isOlderThan(ClientVersion.V_1_9) ? 5.0 : 4.5;
        }
        range += PLACEMENT_RANGE_MARGIN;
        final double rangeSquared = range * range;

        final Vector3d cursor = new Vector3d(
                support.getX() + clamp01(place.cursor.getX()),
                support.getY() + clamp01(place.cursor.getY()),
                support.getZ() + clamp01(place.cursor.getZ()));

        final List<Vector3d> eyes = new ArrayList<>();
        for (double eyeHeight : eyeHeights) {
            eyes.addAll(eyePositions(eyeHeight));
        }

        // If the clicked point itself is outside reach, this is FarPlace territory.
        if (!anyEyeWithin(cursor, eyes, rangeSquared)) return;

        final StateType heldItem = place.itemStack.getType().getPlacedType();
        final List<SimpleCollisionBox> supportBoxes = new ArrayList<>();
        HitboxData.getBlockHitbox(player, heldItem, player.getClientVersion(), state, false,
                support.getX(), support.getY(), support.getZ()).downCast(supportBoxes);
        if (supportBoxes.isEmpty()) return;

        final boolean legacy = player.getClientVersion().isOlderThan(ClientVersion.V_1_9);
        final double tolerance = legacy ? LEGACY_CURSOR_TOLERANCE : MODERN_CURSOR_TOLERANCE;
        final int steps = legacy ? 2 : 1;
        final double spacing = tolerance / steps;

        final List<Vector3d> samples = new ArrayList<>();
        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                final Vector3d sample = samplePoint(support, cursor, place.getFace(), i * spacing, j * spacing);
                if (sample == null || !insideAnyBox(sample, supportBoxes)) continue;
                samples.add(sample);
            }
        }
        if (samples.isEmpty()) return;

        Vector3i firstOccluder = null;
        long oldestOccluderAge = Long.MAX_VALUE;
        boolean freshForeignOccluder = false;
        final int pingThreshold = player.getTransactionPing();

        for (Vector3d eye : eyes) {
            for (Vector3d sample : samples) {
                final double sampleDistanceSquared = eye.distanceSquared(
                        sample.getX(), sample.getY(), sample.getZ());
                if (sampleDistanceSquared > rangeSquared) continue;

                final HitData hit = traceBlock(eye, extendPast(eye, sample), heldItem, support);
                if (hit == null) continue;

                if (hit.position().equals(support)) {
                    // An eye inside the support is a different problem; do not turn
                    // the support's own exit face into a wall.
                    final double selfHitDistanceSquared = hit.blockHitLocation().distanceSquared(
                            eye.getX(), eye.getY(), eye.getZ());
                    if (selfHitDistanceSquared < MIN_OCCLUDER_HIT_DISTANCE_SQ) {
                        return;
                    }
                    // The ray reaches the support, but only the clicked face being
                    // the first face it meets makes the sample visible. A ray that
                    // reaches the support's front face does not make its hidden back
                    // face a legal click. Non-solid supports still accept: their
                    // target shape is what the client picks, and the collision/outline
                    // split is not reliable enough to turn that into a flag.
                    if (hit.closestDirection() == place.getFace() || !state.getType().isSolid()) {
                        // One clear point on the clicked face is enough.
                        return;
                    }
                    if (firstOccluder == null) firstOccluder = support;
                    oldestOccluderAge = Math.min(oldestOccluderAge, supportAge);
                    if (supportAge < MIN_BLOCK_AGE_MS + pingThreshold) {
                        freshForeignOccluder = true;
                    }
                    continue;
                }
                if (isLenientOccluder(hit.state().getType())) {
                    // The target outline can differ from the collision shape here.
                    return;
                }

                final double hitDistanceSquared = hit.blockHitLocation().distanceSquared(
                        eye.getX(), eye.getY(), eye.getZ());
                if (hitDistanceSquared < MIN_OCCLUDER_HIT_DISTANCE_SQ) {
                    // The eye is inside a block; that is a different problem.
                    return;
                }
                // A hit past the cursor is not proof that the cursor is hidden.
                if (hitDistanceSquared > sampleDistanceSquared + 0.01) continue;

                if (firstOccluder == null) {
                    firstOccluder = hit.position();
                }
                final long age = player.compensatedWorld.getBlockAgeMillis(
                        hit.position().getX(), hit.position().getY(), hit.position().getZ());
                oldestOccluderAge = Math.min(oldestOccluderAge, age);
                if (!recentPlacements.containsKey(hit.position())
                        && age < MIN_BLOCK_AGE_MS + pingThreshold) {
                    freshForeignOccluder = true;
                }
            }
        }

        if (firstOccluder == null || freshForeignOccluder) return;

        flag("action=place support=" + support.getX() + "," + support.getY() + "," + support.getZ()
                + " occluder=" + firstOccluder.getX() + "," + firstOccluder.getY() + "," + firstOccluder.getZ()
                + " age=" + oldestOccluderAge + "ms samples=" + samples.size());
    }

    private HitData traceBlock(Vector3d start, Vector3d end) {
        final StateType heldItem = player.inventory.getHeldItem().getType().getPlacedType();
        return traceBlock(start, end, heldItem, null);
    }

    private HitData traceBlock(Vector3d start, Vector3d end, StateType heldItem, @Nullable Vector3i support) {
        return WorldRayTrace.traverseBlocks(player, start, end, (block, position) -> {
            // Non-solid blocks (torches, vines, fire, rails, pressure plates, ...)
            // have target boxes in HitboxData but are ignored by the 1.8 block pick
            // (and, conservatively, by this check on modern clients). The clicked
            // support itself is still considered: a modern client can target a
            // torch or vine, and its own outline is a real pick.
            if ((support == null || !position.equals(support)) && !block.getType().isSolid()) {
                return null;
            }

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

    private double[] eyeHeights() {
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
            // 1.8 only returns the current stance's pair. The click can land while
            // the client is interpolating the sneak pose, so consider both pairs.
            final double[] standing = player.possibleEyeHeights[0];
            final double[] sneaking = player.possibleEyeHeights[1];
            return new double[]{standing[0], standing[1], sneaking[0], sneaking[1]};
        }
        return player.getPossibleEyeHeights();
    }

    private List<Vector3d> eyePositions(double eyeHeight) {
        final List<Vector3d> eyes = new ArrayList<>(3);
        addEye(eyes, player.x, player.y + eyeHeight, player.z);
        addEye(eyes, player.lastX, player.lastY + eyeHeight, player.lastZ);
        addEye(eyes, (player.x + player.lastX) / 2.0,
                (player.y + player.lastY) / 2.0 + eyeHeight,
                (player.z + player.lastZ) / 2.0);
        return eyes;
    }

    private static void addEye(List<Vector3d> eyes, double x, double y, double z) {
        for (Vector3d eye : eyes) {
            if (eye.distanceSquared(x, y, z) < 1e-12) return;
        }
        eyes.add(new Vector3d(x, y, z));
    }

    private static boolean anyEyeWithin(Vector3d point, List<Vector3d> eyes, double rangeSquared) {
        for (Vector3d eye : eyes) {
            if (eye.distanceSquared(point.getX(), point.getY(), point.getZ()) <= rangeSquared) return true;
        }
        return false;
    }

    private static Vector3d samplePoint(Vector3i support, Vector3d cursor, BlockFace face, double a, double b) {
        double x = cursor.getX();
        double y = cursor.getY();
        double z = cursor.getZ();

        switch (face) {
            case UP, DOWN -> {
                x = clampFace(x + a, support.getX());
                z = clampFace(z + b, support.getZ());
            }
            case NORTH, SOUTH -> {
                x = clampFace(x + a, support.getX());
                y = clampFace(y + b, support.getY());
            }
            case WEST, EAST -> {
                y = clampFace(y + a, support.getY());
                z = clampFace(z + b, support.getZ());
            }
            default -> {
                return null;
            }
        }

        return new Vector3d(x, y, z);
    }

    private static Vector3d extendPast(Vector3d eye, Vector3d sample) {
        final double dx = sample.getX() - eye.getX();
        final double dy = sample.getY() - eye.getY();
        final double dz = sample.getZ() - eye.getZ();
        final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) return sample;

        final double factor = SAMPLE_EXTENSION / length;
        return new Vector3d(sample.getX() + dx * factor,
                sample.getY() + dy * factor,
                sample.getZ() + dz * factor);
    }

    private static boolean insideAnyBox(Vector3d point, List<SimpleCollisionBox> boxes) {
        for (SimpleCollisionBox box : boxes) {
            if (point.getX() >= box.minX - 1e-6 && point.getX() <= box.maxX + 1e-6
                    && point.getY() >= box.minY - 1e-6 && point.getY() <= box.maxY + 1e-6
                    && point.getZ() >= box.minZ - 1e-6 && point.getZ() <= box.maxZ + 1e-6) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLenientOccluder(StateType type) {
        // These blocks can render a target outline that is smaller than (or shaped
        // differently from) the collision box used by the block trace, or have no
        // collision box at all on 1.8 while still producing a target box here.
        return type == StateTypes.CHEST || type == StateTypes.TRAPPED_CHEST
                || type == StateTypes.ENDER_CHEST || type == StateTypes.BARRIER
                || type == StateTypes.LECTERN || type == StateTypes.SCAFFOLDING
                || type == StateTypes.FIRE || type == StateTypes.SOUL_FIRE;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampFace(double value, int origin) {
        return Math.max(origin + FACE_EDGE_EPSILON,
                Math.min(origin + 1.0 - FACE_EDGE_EPSILON, value));
    }

    private void pruneRecentPlacements(long now) {
        recentPlacements.entrySet().removeIf(entry -> now - entry.getValue() > RECENT_OWN_PLACEMENT_MS);
    }
}
