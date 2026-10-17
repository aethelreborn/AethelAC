package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.data.HitData;
import ac.aethel.aethelac.utils.data.packetentity.PacketEntity;
import ac.aethel.aethelac.utils.nmsutil.Collisions;
import ac.aethel.aethelac.utils.nmsutil.WorldRayTrace;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Detects precision-tracking of fully occluded players.
 *
 * <p>Every behavioral aim check is a threshold guess, but one thing is close
 * to an invariant: the vanilla client cannot show a player behind solid
 * blocks, so no legitimate input stream can hold the crosshair within a few
 * degrees of a hidden player's hitbox center for half a second while that
 * player moves. Cheat clients knowing the position via ESP do exactly that,
 * even with humanized smoothing, because they are steering toward
 * information the player does not have.</p>
 *
 * <p>Occlusion is a collision-shape ray from the eye to the target center
 * (glass is exempt - players do see through it), so slabs, doors and other
 * partial blocks never count. A tracked target must move at least
 * {@code hidden-aim-move-blocks} during the tracking run, which excludes
 * pre-aiming at remembered or stationary spots.</p>
 */
@CheckData(name = "HiddenAim", stableKey = "aethel.aim.hidden_aim",
        description = "Precision-tracked a moving player through solid blocks", experimental = true)
public class HiddenAim extends Check implements RotationListener {
    private static final double SEGMENT_EPSILON = 1.0E-9;

    private double maxRange;
    private double toleranceCos;
    private int minTicks;
    private double moveBlocks;
    private long cooldownNanos;

    private final Int2ObjectOpenHashMap<TrackInfo> tracked = new Int2ObjectOpenHashMap<>(16);
    private long lastFlagNanos;

    public HiddenAim(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxRange = Math.max(4.0D, config.getDoubleElse("aim-analysis.hidden-aim-range", 24.0D));
        double toleranceDeg = Math.min(30.0D, Math.max(0.5D, config.getDoubleElse("aim-analysis.hidden-aim-tolerance-deg", 3.0D)));
        toleranceCos = Math.cos(Math.toRadians(toleranceDeg));
        minTicks = Math.max(3, config.getIntElse("aim-analysis.hidden-aim-min-ticks", 10));
        moveBlocks = Math.max(0.1D, config.getDoubleElse("aim-analysis.hidden-aim-move-blocks", 0.75D));
        cooldownNanos = Math.max(1, config.getIntElse("aim-analysis.hidden-aim-cooldown-seconds", 5)) * 1_000_000_000L;
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.gamemode == GameMode.SPECTATOR || player.inVehicle() || !player.cameraEntity.isSelf()) {
            tracked.clear();
            return;
        }

        double yaw = Math.toRadians(rotationUpdate.newYaw());
        double pitch = Math.toRadians(rotationUpdate.newPitch());
        double dirX = -Math.sin(yaw) * Math.cos(pitch);
        double dirY = -Math.sin(pitch);
        double dirZ = Math.cos(yaw) * Math.cos(pitch);
        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        for (Map.Entry<Integer, PacketEntity> entry : player.compensatedEntities.entityMap.int2ObjectEntrySet()) {
            PacketEntity entity = entry.getValue();
            if (entity == player.compensatedEntities.self || entity.isDead) continue;
            if (!EntityTypes.PLAYER.equals(entity.getType())) continue;

            Vector3d serverPos = entity.trackedServerPosition.getPos();
            double approxDx = serverPos.x - eyeX;
            double approxDz = serverPos.z - eyeZ;
            if (approxDx * approxDx + approxDz * approxDz > maxRange * maxRange) {
                tracked.remove(entry.getKey().intValue());
                continue;
            }

            SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
            double cx = (box.minX + box.maxX) / 2.0D;
            double cy = (box.minY + box.maxY) / 2.0D;
            double cz = (box.minZ + box.maxZ) / 2.0D;

            double dx = cx - eyeX;
            double dy = cy - eyeY;
            double dz = cz - eyeZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > maxRange || distance < 1.2D) {
                tracked.remove(entry.getKey().intValue());
                continue;
            }

            double dot = (dx * dirX + dy * dirY + dz * dirZ) / distance;
            if (dot < toleranceCos || !isOccluded(eyeX, eyeY, eyeZ, cx, cy, cz)) {
                tracked.remove(entry.getKey().intValue());
                continue;
            }

            TrackInfo info = tracked.get(entry.getIntKey());
            if (info == null) {
                info = new TrackInfo(cx, cy, cz);
                tracked.put(entry.getIntKey(), info);
            }
            info.ticks++;

            if (info.ticks < minTicks) continue;

            double movedX = cx - info.entryX;
            double movedY = cy - info.entryY;
            double movedZ = cz - info.entryZ;
            if (movedX * movedX + movedY * movedY + movedZ * movedZ < moveBlocks * moveBlocks) continue;

            long now = System.nanoTime();
            if (now - lastFlagNanos < cooldownNanos) continue;

            lastFlagNanos = now;
            tracked.clear();
            flag(String.format(Locale.ROOT,
                    "tracked a hidden player for %d rotation updates while it moved %.2f blocks",
                    info.ticks, Math.sqrt(movedX * movedX + movedY * movedY + movedZ * movedZ)));
        }
    }

    private boolean isOccluded(double startX, double startY, double startZ, double endX, double endY, double endZ) {
        HitData hit = WorldRayTrace.traverseBlocks(player,
                new Vector3d(startX, startY, startZ), new Vector3d(endX, endY, endZ),
                (state, pos) -> {
                    StateType type = state.getType();
                    if (type.isAir() || !type.isSolid()) return null;
                    if (type.getName().toString().contains("glass")) return null;
                    return new HitData(pos, null, null, state);
                });
        if (hit == null) return false;

        Vector3i pos = hit.position();
        List<SimpleCollisionBox> boxes = new ArrayList<>(2);
        SimpleCollisionBox cell = new SimpleCollisionBox(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        Collisions.getCollisionBoxes(player, cell, boxes, false);
        for (SimpleCollisionBox box : boxes) {
            if (segmentIntersectsBox(startX, startY, startZ, endX, endY, endZ, box)) {
                return true;
            }
        }

        return false;
    }

    private static boolean segmentIntersectsBox(double startX, double startY, double startZ,
                                                double endX, double endY, double endZ,
                                                SimpleCollisionBox box) {
        double[] start = {startX, startY, startZ};
        double[] delta = {endX - startX, endY - startY, endZ - startZ};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};

        double tMin = 0.0D;
        double tMax = 1.0D;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < SEGMENT_EPSILON) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return false;
                continue;
            }
            double t1 = (min[axis] - start[axis]) / delta[axis];
            double t2 = (max[axis] - start[axis]) / delta[axis];
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        return true;
    }

    private static final class TrackInfo {
        private final double entryX;
        private final double entryY;
        private final double entryZ;
        private int ticks;

        private TrackInfo(double entryX, double entryY, double entryZ) {
            this.entryX = entryX;
            this.entryY = entryY;
            this.entryZ = entryZ;
        }
    }
}
