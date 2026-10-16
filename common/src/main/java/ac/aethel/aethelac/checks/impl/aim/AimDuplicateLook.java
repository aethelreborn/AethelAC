package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;

@CheckData(name = "AimDuplicateLook", stableKey = "aethel.aim.duplicate_look", description = "Sent a duplicate rotation update without changing look direction")
public class AimDuplicateLook extends Check implements RotationListener {
    private boolean exempt;

    public AimDuplicateLook(AethelPlayer player) {
        super(player);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate || player.compensatedEntities.self.getRiding() != null) {
            exempt = true;
            return;
        }

        if (exempt) { // Exempt for a tick on teleport
            exempt = false;
            return;
        }

        if (rotationUpdate.oldYaw() == rotationUpdate.newYaw() && rotationUpdate.oldPitch() == rotationUpdate.newPitch()) {
            flag();
        }
    }
}
