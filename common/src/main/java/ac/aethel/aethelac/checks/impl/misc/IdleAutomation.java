package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PositionListener;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PositionUpdate;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects anti-AFK automation: sparse idle actions (jumps or arm swings)
 * repeated for a long stretch while the player performs no other input.
 *
 * <p>Anti-AFK modules jump or swing every few seconds without rotating or
 * moving, so the server only observes those isolated packets from an otherwise
 * stone-still player. Humans idling at spawn eventually turn the camera, walk
 * a step, or interact with something; holding the attack key (rapid swings)
 * is filtered out by the rate band.</p>
 */
@CheckData(name = "IdleAutomation", stableKey = "aethel.misc.idle_automation", description = "Performed sparse idle actions while fully stationary", experimental = true)
public class IdleAutomation extends Check implements PositionListener, RotationListener, PacketReceiveListener {

    private static final int CAPACITY = 32;
    private static final double STILL_ROTATION_DEG = 0.05D;
    private static final double STILL_HORIZONTAL_BLOCKS = 0.03D;
    private static final double STILL_VERTICAL_BLOCKS = 0.03D;
    private static final double JUMP_VERTICAL_BLOCKS = 0.1D;
    private static final double JUMP_HORIZONTAL_BLOCKS = 0.2D;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] eventTimes = new long[CAPACITY];
    private int eventIndex;
    private int eventSize;

    private long lastRealActivityNanos;
    private long lastInteractionNanos;
    private long lastFlagNanos;
    private final long joinNanos;

    private int windowSeconds;
    private int minEvents;
    private double minIntervalSeconds;
    private double maxIntervalSeconds;
    private long stillnessNanos;

    public IdleAutomation(AethelPlayer player) {
        super(player);
        long now = System.nanoTime();
        joinNanos = now;
        lastRealActivityNanos = now;
        lastInteractionNanos = now;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = config.getIntElse("idle-automation.window-seconds", 180);
        minEvents = config.getIntElse("idle-automation.min-events", 8);
        minIntervalSeconds = config.getDoubleElse("idle-automation.min-interval-seconds", 3.0D);
        maxIntervalSeconds = config.getDoubleElse("idle-automation.max-interval-seconds", 15.0D);
        stillnessNanos = (long) (config.getDoubleElse("idle-automation.stillness-milliseconds", 2000.0D) * 1_000_000L);
    }

    @Override
    public void process(final RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.vehicleData.wasVehicleSwitch
                || player.packetStateData.horseInteractCausedForcedRotation) {
            lastRealActivityNanos = System.nanoTime();
            return;
        }
        if (Math.abs(rotationUpdate.deltaYaw()) > STILL_ROTATION_DEG
                || Math.abs(rotationUpdate.deltaPitch()) > STILL_ROTATION_DEG) {
            lastRealActivityNanos = System.nanoTime();
        }
    }

    @Override
    public void onPositionUpdate(PositionUpdate positionUpdate) {
        long now = System.nanoTime();
        if (positionUpdate.isTeleport()) {
            lastRealActivityNanos = now;
            return;
        }
        double dx = positionUpdate.getTo().x - positionUpdate.getFrom().x;
        double dy = positionUpdate.getTo().y - positionUpdate.getFrom().y;
        double dz = positionUpdate.getTo().z - positionUpdate.getFrom().z;
        double horizontal = Math.hypot(dx, dz);

        boolean still = now - lastRealActivityNanos >= stillnessNanos;
        if (still && dy > JUMP_VERTICAL_BLOCKS && horizontal <= JUMP_HORIZONTAL_BLOCKS) {
            recordIdleEvent(now);
        }

        if (horizontal > STILL_HORIZONTAL_BLOCKS || Math.abs(dy) > STILL_VERTICAL_BLOCKS) {
            lastRealActivityNanos = now;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION || event.getPacketType() == PacketType.Play.Client.PUNCH) {
            if (now - lastRealActivityNanos >= stillnessNanos) {
                recordIdleEvent(now);
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ATTACK
                || event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                || event.getPacketType() == PacketType.Play.Client.USE_ITEM
                || event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING
                || event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            lastInteractionNanos = now;
            lastRealActivityNanos = now;
        }
    }

    private void recordIdleEvent(long now) {
        eventTimes[eventIndex] = now;
        eventIndex = (eventIndex + 1) % CAPACITY;
        if (eventSize < CAPACITY) {
            eventSize++;
        }

        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }
        long windowNanos = windowSeconds * 1_000_000_000L;
        if (now - joinNanos < windowNanos || now - lastInteractionNanos < windowNanos) {
            return;
        }
        long cutoff = now - windowNanos;
        int count = 0;
        long oldest = now;
        for (int k = 0; k < eventSize; k++) {
            int slot = (eventIndex - 1 - k + CAPACITY * 2) % CAPACITY;
            if (eventTimes[slot] <= cutoff) {
                break;
            }
            count++;
            oldest = eventTimes[slot];
        }
        if (count < minEvents || count < 2) {
            return;
        }
        double meanInterval = (now - oldest) / 1_000_000_000.0D / (count - 1);
        if (meanInterval < minIntervalSeconds || meanInterval > maxIntervalSeconds) {
            return;
        }
        flag(String.format(Locale.ROOT, "%d idle actions over %ds with no other input (avg every %.1fs)",
                count, windowSeconds, meanInterval));
        lastFlagNanos = now;
    }

    public int getWindowEventCount() {
        long cutoff = System.nanoTime() - windowSeconds * 1_000_000_000L;
        int count = 0;
        for (int k = 0; k < eventSize; k++) {
            if (eventTimes[(eventIndex - 1 - k + CAPACITY * 2) % CAPACITY] <= cutoff) {
                break;
            }
            count++;
        }
        return count;
    }
}
