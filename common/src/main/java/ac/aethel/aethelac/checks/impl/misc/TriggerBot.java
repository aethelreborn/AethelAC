package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects triggerbots: attacks fired on the first tick a target enters the
 * crosshair of an otherwise static rotation.
 *
 * <p>Triggerbot modules (Releon's default delay is 0 ms, LiquidBounce's
 * tolerance-based modules alike) attack the very tick the client's hit test
 * turns positive, while a human needs at least a visual reaction time from
 * the moment the target slides under a pre-aimed crosshair. Repeated
 * sub-100 ms entries with no aiming motion in between are robotic.</p>
 *
 * <p>Delay-configured modules that mimic 100-120 ms "legit" delays are
 * caught by a second tier separating them from statistically fast humans
 * through crosshair-entry precision rather than a single timing bound: the
 * attack lands while the ray passes within a tenth of a block of the hitbox
 * center, which a human cannot reliably do at that reaction speed.</p>
 *
 * <p>Delayed triggerbots ("metronome" modules) sleep 100-450 ms before
 * attacking, dodging both tiers above: their reaction is too slow for the
 * first tier and imprecise relative to the second. They are separated
 * instead by stream consistency: over the last 20 crosshair-entry
 * opportunities that lasted at least 300 ms, a bot answers nearly every one
 * (ratio &gt;= 0.85) with a mean reaction inside 100-280 ms and a coefficient
 * of variation of at most 0.10 - a beat no human choosing when to click can
 * hold across a whole fight. Modules with longer, narrowly jittered sleeps
 * (up to 450 ms) are covered by a late band requiring a reaction CV of at
 * most 0.04, because their machine-steady beat survives tick quantization
 * where a human's reaction spread cannot.</p>
 */
@CheckData(name = "TriggerBot", stableKey = "aethel.misc.trigger_bot",
        description = "Attacks on the first tick a target enters a static crosshair", experimental = true)
public class TriggerBot extends Check implements RotationListener, PacketReceiveListener, PacketSendListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int METRONOME_MAX_WINDOW = 20;

    private static final class Tracked {
        double x;
        double y;
        double z;
        boolean onCrosshair;
        long enterNanos;
        double entrySpeed;
        long opportunityStartNanos;
        boolean opportunityFired;
        double opportunityRtMs;
    }

    private final Int2ObjectMap<Tracked> tracked = new Int2ObjectOpenHashMap<>();
    private final double[] speedRing = new double[4];
    private int speedIndex;

    private boolean hasRotation;
    private float lastYaw;
    private float lastPitch;
    private long lastRotationNanos;

    private int triggers;
    private long firstTriggerNanos;
    private long lastFlagNanos;

    private final long[] preciseRing = new long[8];
    private int preciseIndex;

    private final boolean[] opportunityFired;
    private final double[] opportunityRtMs;
    private int opportunityIndex;
    private int opportunityCount;

    private double reactionMaxMs;
    private double staticSpeed;
    private int minEvents;
    private double counterSeconds;
    private double reach;
    private int maxPingMs;
    private double preciseReactionMaxMs;
    private double preciseCenterOffset;
    private int preciseMinEvents;
    private double preciseCounterSeconds;
    private double metronomeOpportunityMs;
    private int metronomeWindow;
    private double metronomeMinRatio;
    private double metronomeMinMeanMs;
    private double metronomeMaxMeanMs;
    private double metronomeMaxCv;
    private double metronomeLateMaxMeanMs;
    private double metronomeLateMaxCv;

    public TriggerBot(AethelPlayer player) {
        super(player);
        opportunityFired = new boolean[METRONOME_MAX_WINDOW];
        opportunityRtMs = new double[METRONOME_MAX_WINDOW];
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        reactionMaxMs = config.getDoubleElse("attack-patterns.trigger-reaction-max-ms", 100.0D);
        staticSpeed = config.getDoubleElse("attack-patterns.trigger-static-speed", 1.5D);
        minEvents = Math.max(2, config.getIntElse("attack-patterns.trigger-min-events", 3));
        counterSeconds = config.getDoubleElse("attack-patterns.trigger-counter-seconds", 30.0D);
        reach = config.getDoubleElse("attack-patterns.trigger-reach", 3.5D);
        maxPingMs = config.getIntElse("attack-patterns.timing-max-ping-ms", 150);
        preciseReactionMaxMs = config.getDoubleElse("attack-patterns.trigger-precise-reaction-max-ms", 120.0D);
        preciseCenterOffset = config.getDoubleElse("attack-patterns.trigger-precise-center-offset", 0.10D);
        preciseMinEvents = Math.max(2, config.getIntElse("attack-patterns.trigger-precise-min-events", 4));
        preciseCounterSeconds = config.getDoubleElse("attack-patterns.trigger-precise-counter-seconds", 60.0D);
        metronomeOpportunityMs = Math.max(0.0D, config.getDoubleElse("attack-patterns.trigger-metronome-opportunity-ms", 300.0D));
        metronomeWindow = Math.max(4, Math.min(METRONOME_MAX_WINDOW,
                config.getIntElse("attack-patterns.trigger-metronome-window", METRONOME_MAX_WINDOW)));
        metronomeMinRatio = config.getDoubleElse("attack-patterns.trigger-metronome-min-ratio", 0.85D);
        metronomeMinMeanMs = config.getDoubleElse("attack-patterns.trigger-metronome-min-mean-ms", 100.0D);
        metronomeMaxMeanMs = config.getDoubleElse("attack-patterns.trigger-metronome-max-mean-ms", 280.0D);
        metronomeMaxCv = config.getDoubleElse("attack-patterns.trigger-metronome-max-cv", 0.10D);
        metronomeLateMaxMeanMs = config.getDoubleElse("attack-patterns.trigger-metronome-late-max-mean-ms", 460.0D);
        metronomeLateMaxCv = config.getDoubleElse("attack-patterns.trigger-metronome-late-max-cv", 0.04D);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate
                || player.compensatedEntities.self.getRiding() != null) {
            return;
        }

        double deltaYaw = rotationUpdate.deltaYaw();
        double deltaPitch = rotationUpdate.deltaPitch();
        speedRing[speedIndex] = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        speedIndex = (speedIndex + 1) % speedRing.length;

        lastYaw = rotationUpdate.newYaw();
        lastPitch = rotationUpdate.newPitch();
        lastRotationNanos = System.nanoTime();
        hasRotation = true;

        long now = lastRotationNanos;
        double speed = currentSpeed(now);
        for (Tracked entity : tracked.values()) {
            evaluateCrosshair(entity, now, speed);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
            spawn(packet.getEntityId(), packet.getPosition());
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            EntityType type = packet.getEntityType();
            if (type != null && EntityTypes.isTypeInstanceOf(type, EntityTypes.PLAYER)) {
                spawn(packet.getEntityId(), packet.getPosition());
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
                revalidate(packet.getEntityId(), entity);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            long now = System.nanoTime();
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
                Tracked entity = tracked.remove(id);
                if (entity != null) {
                    resolveOpportunity(entity, now);
                }
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }

        if (player.getTransactionPing() > maxPingMs) {
            return;
        }

        Tracked entity = tracked.get(packet.getEntityId());
        if (entity == null) {
            return;
        }

        long now = System.nanoTime();
        if (entity.onCrosshair && entity.opportunityStartNanos != 0L && !entity.opportunityFired) {
            entity.opportunityFired = true;
            entity.opportunityRtMs = (now - entity.opportunityStartNanos) / 1_000_000.0D;
        }

        if (!entity.onCrosshair || entity.enterNanos == 0L) {
            return;
        }

        long reactionNanos = now - entity.enterNanos;
        boolean staticEntry = entity.entrySpeed <= staticSpeed && !player.packetStateData.lastPacketWasTeleport;

        if (reactionNanos <= (long) (reactionMaxMs * 1_000_000L) && staticEntry) {
            entity.enterNanos = 0L;
            triggers++;
            if (firstTriggerNanos == 0L || now - firstTriggerNanos > (long) (counterSeconds * 1_000_000_000L)) {
                triggers = 1;
                firstTriggerNanos = now;
            }
            if (triggers >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
                flag(String.format(Locale.ROOT,
                        "%d attacks within %.0f ms of a target entering a static crosshair",
                        triggers, reactionMaxMs));
                lastFlagNanos = now;
            }
            return;
        }

        if (reactionNanos <= (long) (preciseReactionMaxMs * 1_000_000L) && staticEntry
                && crosshairCenterOffset(entity) <= preciseCenterOffset) {
            entity.enterNanos = 0L;
            preciseRing[preciseIndex] = now;
            preciseIndex = (preciseIndex + 1) % preciseRing.length;

            int preciseEvents = 0;
            for (long stamp : preciseRing) {
                if (stamp != 0L && now - stamp <= (long) (preciseCounterSeconds * 1_000_000_000L)) {
                    preciseEvents++;
                }
            }

            if (preciseEvents >= preciseMinEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
                flag(String.format(Locale.ROOT,
                        "%d attacks within %.0f ms of a static crosshair entry, crosshair within %.2f blocks of the hitbox center",
                        preciseEvents, preciseReactionMaxMs, preciseCenterOffset));
                lastFlagNanos = now;
                Arrays.fill(preciseRing, 0L);
            }
        }
    }

    private double crosshairCenterOffset(Tracked entity) {
        double yawRad = Math.toRadians(lastYaw);
        double pitchRad = Math.toRadians(lastPitch);
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double vx = entity.x - player.x;
        double vy = entity.y + 0.9D - (player.y + player.getEyeHeight());
        double vz = entity.z - player.z;

        double along = Math.max(0.0D, vx * dirX + vy * dirY + vz * dirZ);
        double px = vx - along * dirX;
        double py = vy - along * dirY;
        double pz = vz - along * dirZ;
        return Math.sqrt(px * px + py * py + pz * pz);
    }

    private void spawn(int id, Vector3d position) {
        Tracked entity = new Tracked();
        entity.x = position.x;
        entity.y = position.y;
        entity.z = position.z;
        entity.opportunityRtMs = -1.0D;
        Tracked previous = tracked.put(id, entity);
        if (previous != null) {
            resolveOpportunity(previous, System.nanoTime());
        }
    }

    private void move(int id, double dx, double dy, double dz) {
        Tracked entity = tracked.get(id);
        if (entity == null) {
            return;
        }
        entity.x += dx;
        entity.y += dy;
        entity.z += dz;
        revalidate(id, entity);
    }

    private void revalidate(int id, Tracked entity) {
        double x = entity.x - player.x;
        double z = entity.z - player.z;
        if (x * x + z * z > 40.0 * 40.0) {
            resolveOpportunity(entity, System.nanoTime());
            tracked.remove(id);
        } else if (hasRotation) {
            long now = System.nanoTime();
            evaluateCrosshair(entity, now, currentSpeed(now));
        }
    }

    private double currentSpeed(long now) {
        if (!hasRotation || now - lastRotationNanos > 250_000_000L) {
            return 0.0D;
        }
        double total = 0.0D;
        for (double speed : speedRing) {
            total += speed;
        }
        return total / speedRing.length;
    }

    private void evaluateCrosshair(Tracked entity, long now, double speed) {
        double dx = entity.x - player.x;
        double dz = entity.z - player.z;
        if (dx * dx + dz * dz > 36.0) {
            if (entity.onCrosshair) {
                resolveOpportunity(entity, now);
            }
            entity.onCrosshair = false;
            entity.enterNanos = 0L;
            return;
        }

        double yawRad = Math.toRadians(lastYaw);
        double pitchRad = Math.toRadians(lastPitch);
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        double hit = rayBox(eyeX, eyeY, eyeZ, dirX, dirY, dirZ,
                entity.x - 0.3, entity.y, entity.z - 0.3,
                entity.x + 0.3, entity.y + 1.8, entity.z + 0.3);

        boolean onCrosshair = hit >= 0.0D && hit <= reach;
        if (onCrosshair && !entity.onCrosshair) {
            entity.enterNanos = now;
            entity.entrySpeed = speed;
            entity.opportunityStartNanos = now;
            entity.opportunityFired = false;
            entity.opportunityRtMs = -1.0D;
        } else if (!onCrosshair && entity.onCrosshair) {
            resolveOpportunity(entity, now);
        }
        entity.onCrosshair = onCrosshair;
    }

    private void resolveOpportunity(Tracked entity, long now) {
        if (entity.opportunityStartNanos != 0L
                && now - entity.opportunityStartNanos >= (long) (metronomeOpportunityMs * 1_000_000L)) {
            recordOpportunity(entity.opportunityFired, entity.opportunityRtMs);
        }
        entity.opportunityStartNanos = 0L;
    }

    private void recordOpportunity(boolean fired, double rtMs) {
        opportunityFired[opportunityIndex] = fired;
        opportunityRtMs[opportunityIndex] = fired ? rtMs : -1.0D;
        opportunityIndex = (opportunityIndex + 1) % opportunityFired.length;
        if (opportunityCount < opportunityFired.length) {
            opportunityCount++;
        }
        evaluateMetronome();
    }

    private void evaluateMetronome() {
        if (opportunityCount < metronomeWindow) {
            return;
        }

        int firedCount = 0;
        double sum = 0.0D;
        double sumSq = 0.0D;
        for (int i = 0; i < metronomeWindow; i++) {
            int slot = Math.floorMod(opportunityIndex - 1 - i, opportunityFired.length);
            if (!opportunityFired[slot]) {
                continue;
            }
            double rt = opportunityRtMs[slot];
            firedCount++;
            sum += rt;
            sumSq += rt * rt;
        }

        if (firedCount < 4 || (double) firedCount / metronomeWindow < metronomeMinRatio) {
            return;
        }

        double mean = sum / firedCount;
        if (mean < metronomeMinMeanMs) {
            return;
        }

        double variance = Math.max(0.0D, sumSq / firedCount - mean * mean);
        double cv = Math.sqrt(variance) / mean;

        double bandMaxMs;
        if (mean <= metronomeMaxMeanMs) {
            bandMaxMs = metronomeMaxMeanMs;
            if (cv > metronomeMaxCv) {
                return;
            }
        } else if (mean <= metronomeLateMaxMeanMs) {
            bandMaxMs = metronomeLateMaxMeanMs;
            if (cv > metronomeLateMaxCv) {
                return;
            }
        } else {
            return;
        }

        long now = System.nanoTime();
        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        flag(String.format(Locale.ROOT,
                "Metronome triggerbot: %d of the last %d crosshair opportunities attacked, mean reaction %.0f ms with cv %.2f in the %.0f-%.0f ms band",
                firedCount, metronomeWindow, mean, cv, metronomeMinMeanMs, bandMaxMs));
        lastFlagNanos = now;
        Arrays.fill(opportunityFired, false);
        Arrays.fill(opportunityRtMs, 0.0D);
        opportunityIndex = 0;
        opportunityCount = 0;
    }

    private static double rayBox(double ex, double ey, double ez, double dx, double dy, double dz,
                                 double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tMin = 0.0D;
        double tMax = Double.MAX_VALUE;

        double[] result = new double[2];

        if (!clipAxis(ex, dx, minX, maxX, result)) {
            return -1.0D;
        }
        tMin = Math.max(tMin, result[0]);
        tMax = Math.min(tMax, result[1]);

        if (!clipAxis(ey, dy, minY, maxY, result)) {
            return -1.0D;
        }
        tMin = Math.max(tMin, result[0]);
        tMax = Math.min(tMax, result[1]);

        if (!clipAxis(ez, dz, minZ, maxZ, result)) {
            return -1.0D;
        }
        tMin = Math.max(tMin, result[0]);
        tMax = Math.min(tMax, result[1]);

        return tMin <= tMax ? tMin : -1.0D;
    }

    private static boolean clipAxis(double origin, double direction, double min, double max, double[] result) {
        if (Math.abs(direction) < 1.0E-9D) {
            if (origin < min || origin > max) {
                return false;
            }
            result[0] = 0.0D;
            result[1] = Double.MAX_VALUE;
            return true;
        }
        double inverse = 1.0D / direction;
        double t1 = (min - origin) * inverse;
        double t2 = (max - origin) * inverse;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        result[0] = t1;
        result[1] = t2;
        return true;
    }
}
