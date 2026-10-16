package ac.aethel.aethelac.predictionengine.predictions.input;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.math.Vector3dm;
import ac.aethel.aethelac.utils.math.VectorUtils;

public record DoubleInput(double sideways, double vertical, double forward) implements Input {
    @Override
    public Vector3dm vector() {
        return new Vector3dm(sideways, vertical, forward);
    }

    @Override
    public Input normalize(AethelPlayer player) {
        double lengthSquared = sideways * sideways + vertical * vertical + forward * forward;
        if (lengthSquared > 1) {
            double d0 = VectorUtils.getVanillaLength(player.getClientVersion(), sideways, vertical, forward);
            return new DoubleInput(sideways / d0, vertical / d0, forward / d0);
        }

        return this;
    }
}
