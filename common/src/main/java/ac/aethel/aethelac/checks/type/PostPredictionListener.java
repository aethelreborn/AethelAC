package ac.aethel.aethelac.checks.type;

import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;

public interface PostPredictionListener {
    void onPredictionComplete(PredictionComplete predictionComplete);
}
