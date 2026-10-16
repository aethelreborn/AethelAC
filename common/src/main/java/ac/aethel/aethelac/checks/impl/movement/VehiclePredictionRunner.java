package ac.aethel.aethelac.checks.impl.movement;

import ac.aethel.aethelac.checks.AethelProcessor;
import ac.aethel.aethelac.checks.type.VehicleListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PositionUpdate;
import ac.aethel.aethelac.utils.anticheat.update.VehiclePositionUpdate;

public class VehiclePredictionRunner extends AethelProcessor implements VehicleListener {
    public VehiclePredictionRunner(AethelPlayer player) {
        super(player);
    }

    @Override
    public void process(final VehiclePositionUpdate vehicleUpdate) {
        // Vehicle onGround = false always
        // We don't do vehicle setbacks because vehicle netcode sucks.
        player.movementCheckRunner.processAndCheckMovementPacket(new PositionUpdate(vehicleUpdate.from(), vehicleUpdate.to(), false, null, null, vehicleUpdate.isTeleport()));
    }
}
