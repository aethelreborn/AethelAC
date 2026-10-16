package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;

// Based on Kauri AimA,
// I also discovered this flaw before open source Kauri, but did not want to open source its detection.
// It works on clients who % 360 their rotation.
@CheckData(name = "AimModulo360", stableKey = "aethel.aim.modulo_360", description = "Sent a large yaw snap", decay = 0.005)
public class AimModulo360 extends Check implements RotationListener {

    private float lastDeltaYaw;

    public AimModulo360(AethelPlayer player) {
        super(player);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        // Exempt for teleport, entering a vehicle due to rotation reset or
        // after forced, client-sided rotation change after interacting with a horse (not necessarily mounting it)
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            lastDeltaYaw = rotationUpdate.deltaYaw();
            return;
        }

        if (player.yaw < 360 && player.yaw > -360 && Math.abs(rotationUpdate.deltaYaw()) > 320 && Math.abs(lastDeltaYaw) < 30) {
            flag();
        } else {
            reward();
        }

        lastDeltaYaw = rotationUpdate.deltaYaw();
    }
}
