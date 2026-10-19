package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects smoothing / lock aim-assist glued to a strafing target through
 * its direction reversals.
 *
 * <p>While a nearby target strafes, the direction from the attacker's eyes
 * to its hitbox center sweeps at a measurable angular rate; every A/D
 * reversal flips that rate's sign. Holding the crosshair within a degree and
 * a half of the hitbox center for ninety percent of the swept ticks, through
 * several reversals, is a sensorimotor impossibility for a human - visual
 * and motor latency alone put a genuinely aimed crosshair several degrees
 * behind a swept target - but is exactly what exponential smoothing
 * aim-assist produces, because it drives the crosshair toward the true
 * direction every tick.</p>
 *
 * <p>Only swept ticks are judged: a tick enters the window when the target
 * direction moved at least {@code tracking-lock-omega-min-deg} per tick
 * (below that the tracking problem is trivial and untelling) and the
 * crosshair is within {@code tracking-lock-capture-deg} of the target. A
 * swept tick is locked when the crosshair is within
 * {@code tracking-lock-lock-deg}. Flags require at least
 * {@code tracking-lock-min-samples} swept ticks, at least
 * {@code tracking-lock-min-reversals} direction reversals (sign flips at or
 * above {@code tracking-lock-reversal-floor-deg}) and a locked fraction of
 * {@code tracking-lock-fraction} inside the trailing
 * {@code tracking-lock-window-ticks} window.</p>
 *
 * <p>Straight-line motion without reversals is exempt by design (a human can
 * predictively track a uniformly moving target), so runners and knockback
 * arcs are not judged until they start fighting with their strafe keys.
 * Mounted targets whose rider never sends positions, spectator/vehicle
 * attackers, teleports and 1.17 duplicate packets are all excluded or
 * restart the direction series.</p>
 */
@CheckData(name = "TrackingLock", stableKey = "aethel.aim.tracking_lock",
        description = "Held sub-degree lock on a strafing target's hitbox center through direction reversals",
        experimental = true)
public class TrackingLock extends Check implements RotationListener, PacketSendListener {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int MAX_RING = 128;
    private static final double MIN_RANGE = 1.2D;
    private static final double MIN_STEP_TICKS = 0.5D;
    private static final double MAX_STEP_TICKS = 8.0D;

    private static final class Tracked {
        double x;
        double y;
        double z;
        long lastEvalNanos;
        double lastYaw;
        boolean hasDirection;
        double prevOmega;
        boolean hasOmega;

        final long[] entryNanos = new long[MAX_RING];
        final boolean[] entrySampled = new boolean[MAX_RING];
        final boolean[] entryLocked = new boolean[MAX_RING];
        final boolean[] entryReversal = new boolean[MAX_RING];
        int head;
        int count;
        int samples;
        int locked;
        int reversals;

        void restart(double yaw, long now) {
            lastYaw = yaw;
            lastEvalNanos = now;
            hasDirection = true;
            hasOmega = false;
            prevOmega = 0.0D;
        }

        void expire(long now, long windowNanos) {
            while (count > 0) {
                int oldest = Math.floorMod(head - count, MAX_RING);
                if (now - entryNanos[oldest] <= windowNanos) {
                    return;
                }
                if (entrySampled[oldest]) samples--;
                if (entryLocked[oldest]) locked--;
                if (entryReversal[oldest]) reversals--;
                count--;
            }
        }

        void append(long nanos, boolean sampled, boolean lockedHit, boolean reversal) {
            if (count == MAX_RING) {
                int oldest = Math.floorMod(head - count, MAX_RING);
                if (entrySampled[oldest]) samples--;
                if (entryLocked[oldest]) locked--;
                if (entryReversal[oldest]) reversals--;
                count--;
            }
            int slot = Math.floorMod(head, MAX_RING);
            entryNanos[slot] = nanos;
            entrySampled[slot] = sampled;
            entryLocked[slot] = lockedHit;
            entryReversal[slot] = reversal;
            head++;
            count++;
            if (sampled) samples++;
            if (lockedHit) locked++;
            if (reversal) reversals++;
        }

        void clearRing() {
            count = 0;
            samples = 0;
            locked = 0;
            reversals = 0;
        }
    }

    private final Int2ObjectMap<Tracked> tracked = new Int2ObjectOpenHashMap<>();

    private double maxRange;
    private double captureDeg;
    private double lockDeg;
    private double omegaMinDeg;
    private double reversalFloorDeg;
    private int windowTicks;
    private long windowNanos;
    private int minSamples;
    private int minReversals;
    private double lockFraction;
    private long cooldownNanos;

    private boolean hasRotation;
    private float lastYaw;
    private long lastFlagNanos;

    public TrackingLock(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxRange = Math.min(48.0D, Math.max(4.0D, config.getDoubleElse("aim-analysis.tracking-lock-range", 24.0D)));
        captureDeg = Math.min(60.0D, Math.max(5.0D, config.getDoubleElse("aim-analysis.tracking-lock-capture-deg", 25.0D)));
        lockDeg = Math.min(10.0D, Math.max(0.1D, config.getDoubleElse("aim-analysis.tracking-lock-lock-deg", 1.5D)));
        omegaMinDeg = Math.min(20.0D, Math.max(0.2D, config.getDoubleElse("aim-analysis.tracking-lock-omega-min-deg", 1.5D)));
        reversalFloorDeg = Math.min(5.0D, Math.max(0.01D, config.getDoubleElse("aim-analysis.tracking-lock-reversal-floor-deg", 0.1D)));
        windowTicks = Math.max(10, Math.min(MAX_RING, config.getIntElse("aim-analysis.tracking-lock-window-ticks", 60)));
        windowNanos = windowTicks * NANOS_PER_TICK;
        minSamples = Math.max(4, Math.min(windowTicks, config.getIntElse("aim-analysis.tracking-lock-min-samples", 20)));
        minReversals = Math.max(1, Math.min(windowTicks, config.getIntElse("aim-analysis.tracking-lock-min-reversals", 3)));
        lockFraction = Math.min(1.0D, Math.max(0.5D, config.getDoubleElse("aim-analysis.tracking-lock-fraction", 0.9D)));
        cooldownNanos = Math.max(1, config.getIntElse("aim-analysis.tracking-lock-cooldown-seconds", 5)) * 1_000_000_000L;
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.gamemode == GameMode.SPECTATOR || player.inVehicle() || !player.cameraEntity.isSelf()) {
            tracked.clear();
            return;
        }
        if (player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
            return;
        }
        lastYaw = rotationUpdate.newYaw();
        hasRotation = true;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
            if (packet.getEntityId() != player.entityID) {
                spawn(packet.getEntityId(), packet.getPosition());
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            if (packet.getEntityId() != player.entityID) {
                EntityType type = packet.getEntityType();
                if (type != null && EntityTypes.isTypeInstanceOf(type, EntityTypes.PLAYER)) {
                    spawn(packet.getEntityId(), packet.getPosition());
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
            move(packet.getEntityId(), packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            move(packet.getEntityId(), packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
            Tracked entity = tracked.get(packet.getEntityId());
            if (entity != null) {
                Vector3d position = packet.getPosition();
                entity.x = position.x;
                entity.y = position.y;
                entity.z = position.z;
                entity.hasDirection = false;
                entity.hasOmega = false;
                evaluate(entity, System.nanoTime());
            }
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
                tracked.remove(id);
            }
        }
    }

    private void spawn(int id, Vector3d position) {
        Tracked entity = new Tracked();
        entity.x = position.x;
        entity.y = position.y;
        entity.z = position.z;
        tracked.put(id, entity);
    }

    private void move(int id, double dx, double dy, double dz) {
        Tracked entity = tracked.get(id);
        if (entity == null) {
            return;
        }
        entity.x += dx;
        entity.y += dy;
        entity.z += dz;
        double x = entity.x - player.x;
        double z = entity.z - player.z;
        if (x * x + z * z > maxRange * maxRange * 4.0D) {
            tracked.remove(id);
            return;
        }
        evaluate(entity, System.nanoTime());
    }

    private void evaluate(Tracked entity, long now) {
        if (!hasRotation) {
            return;
        }
        double dx = entity.x - player.x;
        double dz = entity.z - player.z;
        double distanceSq = dx * dx + dz * dz;
        if (distanceSq < MIN_RANGE * MIN_RANGE || distanceSq > maxRange * maxRange
                || player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
            entity.hasDirection = false;
            entity.hasOmega = false;
            return;
        }

        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
        if (!entity.hasDirection || entity.lastEvalNanos == 0L) {
            entity.restart(yawTo, now);
            return;
        }

        double stepTicks = (now - entity.lastEvalNanos) / (double) NANOS_PER_TICK;
        if (stepTicks < MIN_STEP_TICKS) {
            // Fold sub-tick updates into the next evaluation without
            // discarding their angular contribution.
            entity.lastYaw = yawTo;
            entity.lastEvalNanos = now;
            return;
        }
        if (stepTicks > MAX_STEP_TICKS) {
            entity.restart(yawTo, now);
            return;
        }

        double omega = wrapDeg(yawTo - entity.lastYaw) / stepTicks;
        entity.lastYaw = yawTo;
        entity.lastEvalNanos = now;

        boolean reversal = false;
        if (entity.hasOmega && entity.prevOmega * omega < 0.0D
                && Math.min(Math.abs(entity.prevOmega), Math.abs(omega)) >= reversalFloorDeg) {
            reversal = true;
        }
        entity.prevOmega = omega;
        entity.hasOmega = true;

        double error = Math.abs(wrapDeg(lastYaw - yawTo));
        if (error > captureDeg) {
            entity.expire(now, windowNanos);
            return;
        }

        boolean armed = Math.abs(omega) >= omegaMinDeg;
        boolean locked = armed && error <= lockDeg;
        entity.expire(now, windowNanos);
        if (armed || reversal) {
            entity.append(now, armed, locked, reversal);
        }

        if (entity.samples >= minSamples && entity.reversals >= minReversals
                && entity.locked * 1.0D / entity.samples >= lockFraction
                && now - lastFlagNanos >= cooldownNanos) {
            lastFlagNanos = now;
            int lockedTicks = entity.locked;
            int sweptTicks = entity.samples;
            int flips = entity.reversals;
            entity.clearRing();
            flag(String.format(Locale.ROOT,
                    "held a %.1f deg lock through %d direction reversals over %d of the last %d swept ticks",
                    lockDeg, flips, lockedTicks, sweptTicks));
        }
    }

    private static double wrapDeg(double deg) {
        deg %= 360.0D;
        if (deg >= 180.0D) deg -= 360.0D;
        if (deg < -180.0D) deg += 360.0D;
        return deg;
    }
}
