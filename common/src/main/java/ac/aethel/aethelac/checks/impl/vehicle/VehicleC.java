package ac.aethel.aethelac.checks.impl.vehicle;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.player.AethelPlayer;

@CheckData(name = "VehicleC", stableKey = "aethel.vehicle.vehicle_control", description = "Moved a vehicle in a way that did not match predicted vehicle control")
public class VehicleC extends Check {
    public VehicleC(AethelPlayer player) {
        super(player);
    }
}
