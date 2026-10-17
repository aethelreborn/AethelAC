package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Detects anchor macros: respawn anchors clicked twice within a single
 * network round trip of each other, cycle after cycle.
 *
 * <p>The charge-use-detonate combos of anchor macros (AutoAnchor defaults to
 * two ticks between steps; "air anchor" is a double click on the same block)
 * produce two interactions on the same anchor position ~100 ms apart, while
 * a human needs to make a decision between charging and detonating. Repeated
 * tight cycles with machine-consistent spacing are automation.</p>
 */
@CheckData(name = "AnchorCadence", stableKey = "aethel.combat.anchor_cadence",
        description = "Respawn anchor clicked twice in quick succession repeatedly", experimental = true)
public class AnchorCadence extends Check implements BlockPlaceListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int CAPACITY = 16;
    private static final int MAX_TRACKED_POSITIONS = 128;

    private final Map<Long, Long> lastClickNanos = new LinkedHashMap<>(16, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > MAX_TRACKED_POSITIONS;
        }
    };
    private final double[] deltasMs = new double[CAPACITY];
    private int deltaCount;
    private int deltaIndex;

    private long lastFlagNanos;

    private double cycleMaxMs;
    private int minCycles;
    private double cadenceMaxCv;
    private int maxPingMs;

    public AnchorCadence(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        cycleMaxMs = config.getDoubleElse("attack-patterns.anchor-cycle-max-ms", 150.0D);
        minCycles = Math.max(4, config.getIntElse("attack-patterns.anchor-min-cycles", 8));
        cadenceMaxCv = config.getDoubleElse("attack-patterns.anchor-cadence-max-cv", 0.6D);
        maxPingMs = config.getIntElse("attack-patterns.timing-max-ping-ms", 150);
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (place.getFace() == BlockFace.OTHER) {
            return; // legacy use-item packets are not block interactions
        }
        if (player.getTransactionPing() > maxPingMs) {
            return;
        }

        Vector3i position = place.position;
        WrappedBlockState state = player.compensatedWorld.getBlock(position);
        if (state.getType() != StateTypes.RESPAWN_ANCHOR) {
            return;
        }

        long now = System.nanoTime();
        long key = packPosition(position);
        Long last = lastClickNanos.put(key, now);
        if (last == null) {
            return;
        }

        double deltaMs = (now - last) / 1_000_000.0D;
        if (deltaMs > cycleMaxMs) {
            return;
        }

        deltasMs[deltaIndex] = deltaMs;
        deltaIndex = (deltaIndex + 1) % CAPACITY;
        if (deltaCount < CAPACITY) {
            deltaCount++;
        }

        if (deltaCount >= minCycles) {
            double mean = 0.0D;
            for (int i = 0; i < deltaCount; i++) {
                mean += deltasMs[i];
            }
            mean /= deltaCount;

            double variance = 0.0D;
            for (int i = 0; i < deltaCount; i++) {
                double diff = deltasMs[i] - mean;
                variance += diff * diff;
            }
            double cv = mean > 0.0D ? Math.sqrt(variance / deltaCount) / mean : 0.0D;

            if (cv <= cadenceMaxCv && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
                flag(String.format(Locale.ROOT,
                        "%d respawn anchor double-clicks %.1f ms apart (%.2f cv)", deltaCount, mean, cv));
                lastFlagNanos = now;
                deltaCount = 0;
                deltaIndex = 0;
            }
        }
    }

    private static long packPosition(Vector3i position) {
        return ((long) (position.getX() & 0x3FFFFFF) << 38)
                | ((long) (position.getY() & 0xFFF) << 26)
                | (position.getZ() & 0x3FFFFFF);
    }
}
