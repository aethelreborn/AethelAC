package ac.aethel.aethelac.predictionengine.predictions.rideable;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.predictionengine.predictions.PredictionEngineWaterLegacy;
import ac.aethel.aethelac.predictionengine.predictions.input.Input;
import ac.aethel.aethelac.utils.data.VectorData;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class PredictionEngineRideableWaterLegacy extends PredictionEngineWaterLegacy {
    private final Input movementVector;

    @Override
    public void addJumpsToPossibilities(AethelPlayer player, Set<VectorData> existingVelocities) {
        PredictionEngineRideableUtils.handleJumps(player, existingVelocities);
    }

    @Override
    public List<VectorData> applyInputsToVelocityPossibilities(AethelPlayer player, Set<VectorData> possibleVectors, float speed) {
        return PredictionEngineRideableUtils.applyInputsToVelocityPossibilities(movementVector, player, possibleVectors, speed);
    }
}
