package ac.aethel.aethelac.checks.type;

import ac.aethel.aethelac.utils.anticheat.update.VehiclePositionUpdate;

public interface VehicleListener {
    void process(final VehiclePositionUpdate vehicleUpdate);
}
