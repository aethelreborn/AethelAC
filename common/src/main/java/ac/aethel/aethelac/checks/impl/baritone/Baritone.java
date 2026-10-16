package ac.aethel.aethelac.checks.impl.baritone;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.impl.aim.processor.AimProcessor;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.aethel.aethelac.utils.math.AethelMath;

// This check has been patched by Baritone for a long time, and it also seems to false with cinematic camera now, so it is disabled.
@CheckData(name = "Baritone", stableKey = "aethel.baritone.baritone", description = "Detected Baritone like behavior")
public class Baritone extends Check implements RotationListener {
    private static final Verbose V = Verbose.of("divisor={f64}");

    private int verbose;

    public Baritone(AethelPlayer player) {
        super(player);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        final float deltaPitch = Math.abs(rotationUpdate.newPitch() - rotationUpdate.oldPitch());

        // Baritone works with small degrees, limit to 1 degree to pick up on baritone slightly moving aim to bypass anticheats
        if (rotationUpdate.deltaYaw() == 0 && deltaPitch > 0 && deltaPitch < 1 && Math.abs(rotationUpdate.newPitch()) != 90.0f) {
            AimProcessor processor = player.checkManager.get(AimProcessor.class);
            if (processor.divisorPitch < AethelMath.MINIMUM_DIVISOR) {
                verbose++;
                if (verbose > 8) {
                    double divisor = AimProcessor.convertToSensitivity(processor.divisorYaw);
                    flag(V.write(verbose()).f64(divisor));
                }
            } else {
                verbose = 0;
            }
        }
    }
}
