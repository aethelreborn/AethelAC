package ac.aethel.aethelac.checks.impl.aim.processor;

import ac.aethel.aethelac.checks.AethelProcessor;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.aethel.aethelac.utils.data.Pair;
import ac.aethel.aethelac.utils.lists.RunningMode;
import ac.aethel.aethelac.utils.math.AethelMath;

public class AimProcessor extends AethelProcessor implements RotationListener {

    private static final int SIGNIFICANT_SAMPLES_THRESHOLD = 15;
    private static final int TOTAL_SAMPLES_THRESHOLD = 80;
    public double sensitivityYaw;
    public double sensitivityPitch;
    public double divisorYaw;
    public double divisorPitch;
    public double modeYaw, modePitch;
    public double deltaDotsYaw, deltaDotsPitch;
    private final RunningMode yawMode = new RunningMode(TOTAL_SAMPLES_THRESHOLD);
    private final RunningMode pitchMode = new RunningMode(TOTAL_SAMPLES_THRESHOLD);
    private float lastYaw;
    private float lastPitch;

    public AimProcessor(AethelPlayer player) {
        super(player);
    }

    public static double convertToSensitivity(double var13) {
        double var11 = var13 / 0.15F / 8.0D;
        double var9 = Math.cbrt(var11);
        return (var9 - 0.2f) / 0.6f;
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        float deltaYaw = rotationUpdate.deltaYawABS();

        this.divisorYaw = AethelMath.gcd(deltaYaw, lastYaw);
        if (deltaYaw > 0 && deltaYaw < 5 && divisorYaw > AethelMath.MINIMUM_DIVISOR) {
            this.yawMode.add(divisorYaw);
            this.lastYaw = deltaYaw;
        }

        float deltaPitch = rotationUpdate.deltaPitchABS();

        this.divisorPitch = AethelMath.gcd(deltaPitch, lastPitch);

        if (deltaPitch > 0 && deltaPitch < 5 && divisorPitch > AethelMath.MINIMUM_DIVISOR) {
            this.pitchMode.add(divisorPitch);
            this.lastPitch = deltaPitch;
        }

        if (this.yawMode.size() > SIGNIFICANT_SAMPLES_THRESHOLD) {
            Pair<Double, Integer> modeYaw = this.yawMode.getMode();
            if (modeYaw.second() > SIGNIFICANT_SAMPLES_THRESHOLD) {
                this.modeYaw = modeYaw.first();
                this.sensitivityYaw = convertToSensitivity(this.modeYaw);
            }
        }
        if (this.pitchMode.size() > SIGNIFICANT_SAMPLES_THRESHOLD) {
            Pair<Double, Integer> modePitch = this.pitchMode.getMode();
            if (modePitch.second() > SIGNIFICANT_SAMPLES_THRESHOLD) {
                this.modePitch = modePitch.first();
                this.sensitivityPitch = convertToSensitivity(this.modePitch);
            }
        }

        this.deltaDotsYaw = deltaYaw / modeYaw;
        this.deltaDotsPitch = deltaPitch / modePitch;
    }
}
