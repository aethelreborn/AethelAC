package ac.aethel.aethelac.predictionengine.movementtick;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.predictionengine.predictions.PredictionEngineLava;
import ac.aethel.aethelac.predictionengine.predictions.PredictionEngineNormal;
import ac.aethel.aethelac.predictionengine.predictions.PredictionEngineWater;
import ac.aethel.aethelac.predictionengine.predictions.PredictionEngineWaterLegacy;
import ac.aethel.aethelac.utils.nmsutil.BlockProperties;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class MovementTickerPlayer extends MovementTicker {
    public MovementTickerPlayer(AethelPlayer player) {
        super(player);
    }

    @Override
    public void doWaterMove(float swimSpeed, boolean isFalling, float swimFriction) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_13)) {
            new PredictionEngineWater().guessBestMovement(swimSpeed, player, isFalling, player.gravity, swimFriction);
        } else {
            new PredictionEngineWaterLegacy().guessBestMovement(swimSpeed, player, swimFriction);
        }
    }

    @Override
    public void doLavaMove() {
        new PredictionEngineLava().guessBestMovement(0.02F, player);
    }

    @Override
    public void doNormalMove(float blockFriction) {
        new PredictionEngineNormal().guessBestMovement(BlockProperties.getFrictionInfluencedSpeed(blockFriction, player), player);
    }
}
