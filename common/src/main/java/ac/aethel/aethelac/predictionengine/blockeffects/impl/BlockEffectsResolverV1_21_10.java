package ac.aethel.aethelac.predictionengine.blockeffects.impl;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.predictionengine.blockeffects.BlockCollisions;
import ac.aethel.aethelac.predictionengine.blockeffects.BlockEffectsResolver;
import ac.aethel.aethelac.predictionengine.blockeffects.BlockStepVisitor;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.math.AethelMath;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.nmsutil.Collisions;
import ac.aethel.aethelac.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

// 1.21.10 ONLY - 1.21.9 is impossible to implement due to Mojang awesomeness
public class BlockEffectsResolverV1_21_10 implements BlockEffectsResolver {

    public static final BlockEffectsResolver INSTANCE = new BlockEffectsResolverV1_21_10();

    @Override
    public void applyEffectsFromBlocks(AethelPlayer player, Vector3dm clientVelocity, boolean onlyApplyVelocity, List<AethelPlayer.Movement> movements) {
        LongSet visitedBlocks = player.visitedBlocks;

        for (AethelPlayer.Movement movement : movements) {
            Vector3d from = movement.from();
            Vector3d to = movement.to().subtract(movement.from());

            int iterationCount = 16;
            if (movement.axisIndependant() && to.lengthSquared() > 0.0) {
                for (Collisions.Axis axis : BlockCollisions.axisStepOrder(movement.axisDependentOriginalMovement())) {
                    double value = axis.get(to);
                    if (value != 0.0) {
                        Vector3d vector = BlockCollisions.relative(from, axis.getPositive(), value);
                        iterationCount -= checkInsideBlocks(player, clientVelocity, onlyApplyVelocity, from, vector, visitedBlocks, iterationCount);
                        from = vector;
                    }
                }
            } else {
                iterationCount -= checkInsideBlocks(player, clientVelocity, onlyApplyVelocity, movement.from(), movement.to(), visitedBlocks, 16);
            }

            if (iterationCount <= 0) {
                checkInsideBlocks(player, clientVelocity, onlyApplyVelocity, movement.to(), movement.to(), visitedBlocks, 1);
            }
        }

        visitedBlocks.clear();
    }

    public static int checkInsideBlocks(AethelPlayer player, Vector3dm clientVelocity, boolean onlyApplyVelocity, Vector3d from, Vector3d to, LongSet visitedBlocks, int count) {
        SimpleCollisionBox boundingBox = GetBoundingBox.getCollisionBoxForPlayer(player, to.x, to.y, to.z).expand(-1.0E-5F);
        boolean isFarEnough = from.distanceSquared(to) > AethelMath.square(0.9999900000002526);

        AtomicInteger blockCount = new AtomicInteger();
        forEachBlockIntersectedBetween(from, to, boundingBox, (blockPos, localCount) -> {
            if (localCount >= count) {
                return false;
            }

            blockCount.set(localCount);

            WrappedBlockState blockState = player.compensatedWorld.getBlock(blockPos);
            StateType blockType = blockState.getType();

            if (blockType.isAir()) {
                return true;
            }

            if (visitedBlocks.add(AethelMath.asLong(blockPos))) {
                boolean shouldApply = isFarEnough || boundingBox.intersects(blockPos);
                Collisions.onInsideBlock(player, clientVelocity, onlyApplyVelocity, blockType, blockState, blockPos.x, blockPos.y, blockPos.z, shouldApply);
            }

            return true;
        });

        return blockCount.get() + 1;
    }

    public static boolean forEachBlockIntersectedBetween(Vector3d start, Vector3d end, SimpleCollisionBox boundingBox, BlockStepVisitor visitor) {
        Vector3d direction = end.subtract(start);
        if (direction.lengthSquared() < (double) AethelMath.square(1.0E-5F)) {
            for (Vector3i blockPos : SimpleCollisionBox.betweenClosed(boundingBox)) {
                if (!visitor.visit(blockPos, 0)) {
                    return false;
                }
            }

            return true;
        } else {
            LongSet alreadyVisited = new LongOpenHashSet();

            for (Vector3i blockPos : SimpleCollisionBox.betweenCornersInDirection(boundingBox.move(direction.multiply(-1.0)), direction)) {
                if (!visitor.visit(blockPos, 0)) {
                    return false;
                }

                alreadyVisited.add(AethelMath.asLong(blockPos));
            }

            int iterationCount = addCollisionsAlongTravel(alreadyVisited, direction, boundingBox, visitor);
            if (iterationCount < 0) {
                return false;
            } else {
                for (Vector3i blockPos : SimpleCollisionBox.betweenCornersInDirection(boundingBox, direction)) {
                    if (alreadyVisited.add(AethelMath.asLong(blockPos)) && !visitor.visit(blockPos, iterationCount + 1)) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    public static int addCollisionsAlongTravel(LongSet alreadyVisited, Vector3d direction, SimpleCollisionBox boundingBox, BlockStepVisitor visitor) {
        double sizeX = boundingBox.getXSize();
        double sizeY = boundingBox.getYSize();
        double sizeZ = boundingBox.getZSize();

        Vector3i furthestCorner = BlockCollisions.getFurthestCorner(direction);

        Vector3d center = boundingBox.getCenter();
        Vector3d end = new Vector3d(
                center.x + sizeX * 0.5 * furthestCorner.getX(),
                center.y + sizeY * 0.5 * furthestCorner.getY(),
                center.z + sizeZ * 0.5 * furthestCorner.getZ()
        );
        Vector3d start = end.subtract(direction);

        int currentX = AethelMath.floor(start.x);
        int currentY = AethelMath.floor(start.y);
        int currentZ = AethelMath.floor(start.z);
        int stepX = AethelMath.sign(direction.x);
        int stepY = AethelMath.sign(direction.y);
        int stepZ = AethelMath.sign(direction.z);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : stepX / direction.x;
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : stepY / direction.y;
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : stepZ / direction.z;
        double tDeltaX = tMaxX * (stepX > 0 ? 1.0 - AethelMath.frac(start.x) : AethelMath.frac(start.x));
        double tDeltaY = tMaxY * (stepY > 0 ? 1.0 - AethelMath.frac(start.y) : AethelMath.frac(start.y));
        double tDeltaZ = tMaxZ * (stepZ > 0 ? 1.0 - AethelMath.frac(start.z) : AethelMath.frac(start.z));
        int iterationCount = 0;

        while (tDeltaX <= 1.0 || tDeltaY <= 1.0 || tDeltaZ <= 1.0) {
            if (tDeltaX < tDeltaY) {
                if (tDeltaX < tDeltaZ) {
                    currentX += stepX;
                    tDeltaX += tMaxX;
                } else {
                    currentZ += stepZ;
                    tDeltaZ += tMaxZ;
                }
            } else if (tDeltaY < tDeltaZ) {
                currentY += stepY;
                tDeltaY += tMaxY;
            } else {
                currentZ += stepZ;
                tDeltaZ += tMaxZ;
            }

            Optional<Vector3d> collisionPoint = BlockCollisions.clip(currentX, currentY, currentZ, currentX + 1, currentY + 1, currentZ + 1, start, end);
            if (collisionPoint.isPresent()) {
                iterationCount++;
                Vector3d collisionVec = collisionPoint.get();
                double clampedX = AethelMath.clamp(collisionVec.x, currentX + 1.0E-5F, currentX + 1.0 - 1.0E-5F);
                double clampedY = AethelMath.clamp(collisionVec.y, currentY + 1.0E-5F, currentY + 1.0 - 1.0E-5F);
                double clampedZ = AethelMath.clamp(collisionVec.z, currentZ + 1.0E-5F, currentZ + 1.0 - 1.0E-5F);
                int endX = AethelMath.floor(clampedX - sizeX * furthestCorner.getX());
                int endY = AethelMath.floor(clampedY - sizeY * furthestCorner.getY());
                int endZ = AethelMath.floor(clampedZ - sizeZ * furthestCorner.getZ());
                int copyIterationCount = iterationCount;

                for (Vector3i blockPos : SimpleCollisionBox.betweenCornersInDirection(currentX, currentY, currentZ, endX, endY, endZ, direction)) {
                    if (alreadyVisited.add(AethelMath.asLong(blockPos)) && !visitor.visit(blockPos, copyIterationCount)) {
                        return -1;
                    }
                }
            }
        }

        return iterationCount;
    }

}
