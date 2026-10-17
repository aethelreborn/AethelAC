package ac.aethel.aethelac.checks.impl.scaffolding;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockPlaceListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockPlace;
import ac.aethel.aethelac.utils.change.BlockModification;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Detects placement modules clicking blocks that do not exist server-side
 * ("airplace" / placement optimizers).
 *
 * <p>A vanilla client can only send a block interaction for a block it can
 * ray trace, so a legitimate click at a position that is air in our world is
 * always the result of a desync: our world lags behind (recent block history
 * at the position) or the player's own placement was not applied to our world
 * (tracked own placements). If none of those apply, the position has been
 * stable air and the click cannot be real.</p>
 */
@CheckData(name = "AirPlace", stableKey = "aethel.scaffolding.air_place",
        description = "Clicked a nonexistent block", experimental = true)
public class AirPlace extends Check implements BlockPlaceListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int MAX_TRACKED_POSITIONS = 256;
    private static final int MAX_EVENTS = 16;

    private final Map<Long, Long> ownPlacements = new LinkedHashMap<>(16, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
            return size() > MAX_TRACKED_POSITIONS;
        }
    };
    private final long[] eventNanos = new long[MAX_EVENTS];
    private int eventCount;
    private int eventIndex;

    private long lastFlagNanos;

    private int stabilityTicks;
    private long graceNanos;
    private int minEvents;
    private long counterNanos;

    public AirPlace(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        stabilityTicks = Math.max(2, config.getIntElse("placement-analysis.airplace-stability-ticks", 20));
        graceNanos = Math.max(1, config.getIntElse("placement-analysis.airplace-grace-ms", 400)) * 1_000_000L;
        minEvents = Math.max(2, config.getIntElse("placement-analysis.airplace-min-events", 2));
        counterNanos = Math.max(1, config.getIntElse("placement-analysis.airplace-counter-seconds", 30)) * 1_000_000_000L;
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (place.getFace() == BlockFace.OTHER || player.gamemode == GameMode.CREATIVE) {
            return;
        }

        long now = System.nanoTime();
        ownPlacements.put(packPosition(place.getPlacedBlockPos()), now);

        Vector3i position = place.position;
        if (!player.compensatedWorld.getBlock(position).getType().isAir()) {
            return;
        }

        int currentTick = AethelAPI.INSTANCE.getTickManager().currentTick;
        for (BlockModification ignored : player.blockHistory.getRecentModifications(
                modification -> position.equals(modification.location())
                        && currentTick - modification.tick() < stabilityTicks)) {
            return; // our world changed here recently; the client may know better
        }

        Long placedAt = ownPlacements.get(packPosition(position));
        if (placedAt != null && now - placedAt <= graceNanos) {
            return; // the player placed here recently but we refused to update our world
        }

        eventNanos[eventIndex] = now;
        eventIndex = (eventIndex + 1) % MAX_EVENTS;
        if (eventCount < MAX_EVENTS) {
            eventCount++;
        }

        int recent = 0;
        for (int i = 0; i < eventCount; i++) {
            if (now - eventNanos[i] <= counterNanos) {
                recent++;
            }
        }

        if (recent >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "clicked the nonexistent block at %d, %d, %d (%d events)",
                    position.getX(), position.getY(), position.getZ(), recent));
            lastFlagNanos = now;
            eventCount = 0;
            eventIndex = 0;
        }
    }

    private static long packPosition(Vector3i position) {
        return ((long) (position.getX() & 0x3FFFFFF) << 38)
                | ((long) (position.getY() & 0xFFF) << 26)
                | (position.getZ() & 0x3FFFFFF);
    }
}
