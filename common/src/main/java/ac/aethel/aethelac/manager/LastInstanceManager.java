package ac.aethel.aethelac.manager;

import ac.aethel.aethelac.checks.AethelProcessor;
import ac.aethel.aethelac.checks.type.PostPredictionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;
import ac.aethel.aethelac.utils.data.LastInstance;

import java.util.ArrayList;
import java.util.List;

public class LastInstanceManager extends AethelProcessor implements PostPredictionListener {
    private final List<LastInstance> instances = new ArrayList<>();

    public LastInstanceManager(AethelPlayer player) {
        super(player);
    }

    public void addInstance(LastInstance instance) {
        instances.add(instance);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        for (LastInstance instance : instances) {
            instance.tick();
        }
    }
}
