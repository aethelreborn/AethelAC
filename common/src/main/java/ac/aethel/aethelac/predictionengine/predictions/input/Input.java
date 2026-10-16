package ac.aethel.aethelac.predictionengine.predictions.input;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public interface Input {

    Vector3dm vector();

    Input normalize(AethelPlayer player);

    static Input createInput(AethelPlayer player, float sideways, float vertical, float forward) {
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_14)) {
            return new DoubleInput(sideways, vertical, forward);
        } else {
            return new FloatInput(sideways, vertical, forward);
        }
    }

}
