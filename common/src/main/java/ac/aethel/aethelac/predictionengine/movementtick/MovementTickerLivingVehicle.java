package ac.aethel.aethelac.predictionengine.movementtick;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.predictionengine.predictions.input.Input;
import ac.aethel.aethelac.predictionengine.predictions.rideable.PredictionEngineRideableLava;
import ac.aethel.aethelac.predictionengine.predictions.rideable.PredictionEngineRideableNormal;
import ac.aethel.aethelac.predictionengine.predictions.rideable.PredictionEngineRideableWater;
import ac.aethel.aethelac.predictionengine.predictions.rideable.PredictionEngineRideableWaterLegacy;
import ac.aethel.aethelac.utils.nmsutil.BlockProperties;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class MovementTickerLivingVehicle extends MovementTicker {
    protected Input movementInput;

    public MovementTickerLivingVehicle(AethelPlayer player) {
        super(player);
        this.movementInput = Input.createInput(player, 0, 0, 0);
    }

    @Override
    public void doWaterMove(float swimSpeed, boolean isFalling, float swimFriction) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13)) {
            new PredictionEngineRideableWater(movementInput).guessBestMovement(swimSpeed, player, isFalling, player.gravity, swimFriction);
        } else {
            new PredictionEngineRideableWaterLegacy(movementInput).guessBestMovement(swimSpeed, player, swimFriction);
        }
    }

    @Override
    public void doLavaMove() {
        new PredictionEngineRideableLava(movementInput).guessBestMovement(0.02F, player);
    }

    @Override
    public void doNormalMove(float blockFriction) {
        new PredictionEngineRideableNormal(movementInput).guessBestMovement(BlockProperties.getFrictionInfluencedSpeed(blockFriction, player), player);
    }
}
