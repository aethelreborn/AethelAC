package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects rotation streams that interleave non-quantized synthetic deltas
 * with genuine quantized mouse movement.
 *
 * <p>Mouse input is quantized by construction: the vanilla client converts
 * every physical movement into whole multiples of one sensitivity-dependent
 * quantum (0.0096 deg - 0.6144 deg for sensitivities 0-1), so a human (or a
 * smoothing-only cheat) produces deltas that fall onto a grid in delta space.
 * Aim-assist and silent-aim modules inject corrections of their own, which
 * are float values that do not land on that grid. This check therefore only
 * arms when a player <i>demonstrably</i> uses quantized deltas (a well-populated
 * cluster in the delta histogram inside the plausible quantum band) and then
 * counts combat rotation probes that neither sit on the fitted grid nor match
 * any other populated cluster (a sensitivity change, not a violation).</p>
 *
 * <p>The construction makes false positives structural: players who never
 * produce quantized deltas in the band (fully smoothed or cinematic inputs,
 * sub-0.015 quanta from extreme sensitivities, protocol-quantized 1.8
 * rotations at 1.40625 deg) never open the cluster gate, so the probe never
 * arms and the check is silent for them. Only a stream that mixes the two -
 * quantized human movement plus off-grid synthetic corrections - can flag,
 * which is the mixed/intermittent aim-assist signature.</p>
 */
@CheckData(name = "RotationGrid", stableKey = "aethel.aim.rotation_grid",
        description = "Interleaved non-quantized rotation deltas with grid-quantized mouse movement", experimental = true)
public class RotationGrid extends Check implements RotationListener, PacketReceiveListener {
    private static final int WINDOW_SIZE = 256;
    private static final int REFIT_INTERVAL = 8;
    private static final double BUCKET = 1.0E-4D;
    private static final double REFINE_RADIUS = 1.5D;
    private static final double FIT_LO = 0.009D;
    private static final double FIT_HI = 12.0D;
    private static final int CANDIDATE_MIN = 4;
    private static final int MAX_CANDIDATES = 64;
    private static final double PROBE_LO = 0.04D;
    private static final double PROBE_MAX_DEG = 12.0D;
    private static final double PROBE_QUANTA_LIMIT = 150.0D;
    private static final double RELATIVE_TOLERANCE = 0.02D;
    private static final double PITCH_CLAMP_MARGIN = 89.0D;

    private final AxisState yawAxis = new AxisState();
    private final AxisState pitchAxis = new AxisState();

    private double minQuantum;
    private double maxQuantum;
    private int clusterEvidence;
    private double absoluteTolerance;
    private int minViolations;
    private long combatWindowNanos;
    private long flagCooldownNanos;

    private boolean[] probes;
    private int probeIndex;
    private int probeCount;
    private int probeViolations;

    private long lastAttackNanos;
    private long lastFlagNanos;

    public RotationGrid(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minQuantum = Math.max(1.0E-3D, config.getDoubleElse("aim-analysis.grid-min-quantum", 0.015D));
        maxQuantum = Math.max(minQuantum + 0.01D, config.getDoubleElse("aim-analysis.grid-max-quantum", 0.72D));
        clusterEvidence = Math.max(2, config.getIntElse("aim-analysis.grid-cluster-evidence", 8));
        absoluteTolerance = Math.max(1.0E-5D, config.getDoubleElse("aim-analysis.grid-tolerance-deg", 0.004D));
        minViolations = Math.max(2, config.getIntElse("aim-analysis.grid-min-violations", 8));
        int ringSize = Math.max(minViolations, config.getIntElse("aim-analysis.grid-window-samples", 30));
        combatWindowNanos = Math.max(0, config.getIntElse("aim-analysis.grid-combat-window-ms", 3000)) * 1_000_000L;
        flagCooldownNanos = Math.max(1, config.getIntElse("aim-analysis.grid-flag-cooldown-seconds", 5)) * 1_000_000_000L;
        probes = new boolean[ringSize];
        resetProbes();
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.gamemode == GameMode.SPECTATOR || player.inVehicle() || !player.cameraEntity.isSelf()
                || player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
            return;
        }

        long now = System.nanoTime();
        boolean combat = now - lastAttackNanos <= combatWindowNanos;

        update(yawAxis, Math.abs(rotationUpdate.deltaYaw()), combat, now);

        // Near the +/-90 pitch clamp the client clips movements into
        // non-multiples of the quantum, so the pitch axis is skipped there.
        if (Math.abs(rotationUpdate.newPitch()) <= PITCH_CLAMP_MARGIN) {
            update(pitchAxis, Math.abs(rotationUpdate.deltaPitch()), combat, now);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                return;
            }
        } else if (event.getPacketType() != PacketType.Play.Client.ATTACK) {
            return;
        }
        lastAttackNanos = System.nanoTime();
    }

    private void update(AxisState state, double delta, boolean combat, long now) {
        if (delta <= 0.0D) {
            return;
        }

        state.updates++;
        if (delta >= FIT_LO && delta <= FIT_HI) {
            state.window[state.windowIndex] = delta;
            state.windowIndex = (state.windowIndex + 1) % WINDOW_SIZE;
            if (state.windowCount < WINDOW_SIZE) {
                state.windowCount++;
            }
            state.dirty = true;
        }

        if (state.dirty && (state.updates % REFIT_INTERVAL == 0L || state.qest <= 0.0D)) {
            refit(state);
        }

        if (combat) {
            probe(state, delta, now);
        }
    }

    private void probe(AxisState state, double delta, long now) {
        double quantum = state.qest;
        if (quantum <= 0.0D) {
            return;
        }
        double probeHi = Math.min(PROBE_MAX_DEG, PROBE_QUANTA_LIMIT * quantum);
        if (delta < PROBE_LO || delta > probeHi) {
            return;
        }

        double tolerance = Math.max(absoluteTolerance, RELATIVE_TOLERANCE * quantum);
        if (onGrid(delta, quantum, tolerance)) {
            recordProbe(false, now);
            return;
        }

        double alternative = alternativeQuantum(state, delta, tolerance);
        if (alternative > 0.0D) {
            state.qest = alternative;
            recordProbe(false, now);
            return;
        }

        recordProbe(true, now);
    }

    private static boolean onGrid(double delta, double quantum, double tolerance) {
        double multiple = Math.round(delta / quantum);
        if (multiple == 0.0D) {
            multiple = 1.0D;
        }
        return Math.abs(delta - multiple * quantum) <= tolerance;
    }

    private double alternativeQuantum(AxisState state, double delta, double tolerance) {
        double best = 0.0D;
        for (int i = 0; i < state.candidateCount; i++) {
            double candidate = state.candidates[i];
            if (onGrid(delta, candidate, tolerance) && (best == 0.0D || candidate < best)) {
                best = candidate;
            }
        }
        return best;
    }

    private void recordProbe(boolean violation, long now) {
        if (probeCount == probes.length && probes[probeIndex]) {
            probeViolations--;
        } else if (probeCount < probes.length) {
            probeCount++;
        }
        probes[probeIndex] = violation;
        if (violation) {
            probeViolations++;
        }
        probeIndex = (probeIndex + 1) % probes.length;

        if (probeCount < probes.length || probeViolations < minViolations) {
            return;
        }

        int violations = probeViolations;
        int total = probes.length;
        resetProbes();

        if (now - lastFlagNanos < flagCooldownNanos) {
            return;
        }
        lastFlagNanos = now;
        flag(String.format(Locale.ROOT,
                "%d of the last %d armed combat rotation probes were off the quantized mouse grid (yaw q %.4f, pitch q %.4f)",
                violations, total, yawAxis.qest, pitchAxis.qest));
    }

    private void resetProbes() {
        Arrays.fill(probes, false);
        probeIndex = 0;
        probeCount = 0;
        probeViolations = 0;
    }

    private void refit(AxisState state) {
        Int2IntOpenHashMap histogram = state.histogram;
        histogram.clear();
        for (int i = 0; i < state.windowCount; i++) {
            int key = (int) Math.round(state.window[i] / BUCKET);
            histogram.addTo(key, 1);
        }

        int bestKey = Integer.MIN_VALUE;
        double bestCenter = 0.0D;
        for (Int2IntMap.Entry entry : histogram.int2IntEntrySet()) {
            if (entry.getIntValue() < clusterEvidence) {
                continue;
            }
            double center = entry.getIntKey() * BUCKET;
            if (center < minQuantum || center > maxQuantum) {
                continue;
            }
            if (bestKey == Integer.MIN_VALUE || center < bestCenter) {
                bestKey = entry.getIntKey();
                bestCenter = center;
            }
        }

        state.qest = bestKey != Integer.MIN_VALUE ? refine(state, bestCenter) : 0.0D;

        int candidateCount = 0;
        if (bestKey != Integer.MIN_VALUE) {
            for (Int2IntMap.Entry entry : histogram.int2IntEntrySet()) {
                if (entry.getIntValue() < CANDIDATE_MIN || candidateCount >= MAX_CANDIDATES) {
                    continue;
                }
                double center = entry.getIntKey() * BUCKET;
                if (center < minQuantum || center > maxQuantum) {
                    continue;
                }
                state.candidates[candidateCount++] = refine(state, center);
            }
        }
        state.candidateCount = candidateCount;
        state.dirty = false;
    }

    private static double refine(AxisState state, double center) {
        double sum = 0.0D;
        int count = 0;
        for (int i = 0; i < state.windowCount; i++) {
            double value = state.window[i];
            if (Math.abs(value - center) <= REFINE_RADIUS * BUCKET) {
                sum += value;
                count++;
            }
        }
        return count > 0 ? sum / count : center;
    }

    private static final class AxisState {
        private final double[] window = new double[WINDOW_SIZE];
        private final Int2IntOpenHashMap histogram = new Int2IntOpenHashMap();
        private final double[] candidates = new double[MAX_CANDIDATES];
        private int windowIndex;
        private int windowCount;
        private long updates;
        private boolean dirty;
        private double qest;
        private int candidateCount;
    }
}
