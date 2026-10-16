package ac.aethel.aethelac.checks.impl.sprint;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PostPredictionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

@CheckData(name = "SprintF", stableKey = "aethel.sprint.gliding", description = "Sprinting while gliding", experimental = true)
public class SprintF extends Check implements PostPredictionListener {
    public SprintF(AethelPlayer player) {
        super(player);
    }

    @Override
    public boolean isApplicable() {
        return player.getClientVersion() == ClientVersion.V_1_21_4;
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (player.wasGliding && player.isGliding) {
            if (player.isSprinting) {
                flagWithSetback();
            } else {
                reward();
            }
        }
    }
}
