package ac.aethel.aethelac.predictionengine.predictions.input.impl;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.predictionengine.predictions.input.DoubleInput;
import ac.aethel.aethelac.predictionengine.predictions.input.Input;
import ac.aethel.aethelac.predictionengine.predictions.input.InputTransformer;
import ac.aethel.aethelac.utils.math.AethelMath;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.math.VectorUtils;

public class DoubleInputTransformer implements InputTransformer<DoubleInput> { // 1.14 - 1.21.4
    @Override
    public DoubleInput transformInputsToVector(AethelPlayer player, int sideways, int vertical, int forward) {
        float bestPossibleX;
        float bestPossibleZ;

        // Slow movement was determined by the previous pose
        if (player.isSlowMovement) {
            bestPossibleX = sideways * player.sneakingSpeedMultiplier;
            bestPossibleZ = forward * player.sneakingSpeedMultiplier;
        } else {
            bestPossibleX = AethelMath.clamp(sideways, -1, 1);
            bestPossibleZ = AethelMath.clamp(forward, -1, 1);
        }

        if (player.packetStateData.isSlowedByUsingItem()) {
            bestPossibleX *= 0.2F;
            bestPossibleZ *= 0.2F;
        }

        Vector3dm inputVector = new Vector3dm(bestPossibleX, 0, bestPossibleZ);
        inputVector.multiply(0.98F);

        // Simulate float rounding imprecision
        inputVector = new Vector3dm((float) inputVector.getX(), (float) inputVector.getY(), (float) inputVector.getZ());

        if (inputVector.lengthSquared() > 1) {
            double d0 = VectorUtils.getVanillaLength(player.getClientVersion(), inputVector);
            inputVector = new Vector3dm(inputVector.getX() / d0, inputVector.getY() / d0, inputVector.getZ() / d0);
        }

        return new DoubleInput(inputVector.getX(), inputVector.getY(), inputVector.getZ());
    }

    @Override
    public Vector3dm getMovementResultFromInput(AethelPlayer player, Input inputVector, float speed, float yaw) {
        if (!(inputVector instanceof DoubleInput input)) {
            throw new IllegalStateException("Expected input vector of type DoubleInput, but got " + inputVector.getClass().getSimpleName());
        }

        float yawRadians = AethelMath.radians(yaw);
        float sin = player.trigHandler.sin(yawRadians);
        float cos = player.trigHandler.cos(yawRadians);

        double xResult = input.sideways() * cos - input.forward() * sin;
        double zResult = input.forward() * cos + input.sideways() * sin;

        return new Vector3dm(xResult * speed, input.vertical() * speed, zResult * speed);
    }
}
