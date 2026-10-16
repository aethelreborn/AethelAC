package ac.aethel.aethelac.checks.impl.movement;

import ac.aethel.aethelac.checks.AethelProcessor;
import ac.aethel.aethelac.checks.type.PositionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PositionUpdate;

public class PredictionRunner extends AethelProcessor implements PositionListener {

    public PredictionRunner(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPositionUpdate(final PositionUpdate positionUpdate) {
        if (!player.inVehicle()) {
            player.movementCheckRunner.processAndCheckMovementPacket(positionUpdate);
        }
    }
}
