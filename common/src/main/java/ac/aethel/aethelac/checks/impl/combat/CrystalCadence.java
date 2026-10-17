package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects instant crystal auras: end crystals broken within the network
 * round trip of their own spawn repeatedly.
 *
 * <p>A legitimate player must see the crystal spawn, aim at it and click -
 * far longer than the server round trip. Placer/destroyer modules of
 * CrystalAura-style clients attack the spawn packet the moment it arrives,
 * so the server sees the break barely above the ping floor, cycle after
 * cycle, with machine-consistent deltas.</p>
 */
@CheckData(name = "CrystalCadence", stableKey = "aethel.combat.crystal_cadence",
        description = "End crystals broken within the spawn round trip repeatedly", experimental = true)
public class CrystalCadence extends Check implements PacketSendListener, PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int CAPACITY = 16;

    private static final class Crystal {
        long spawnNanos;
        double x;
        double y;
        double z;
    }

    private final Int2ObjectMap<Crystal> crystals = new Int2ObjectOpenHashMap<>();
    private final double[] deltasMs = new double[CAPACITY];
    private int deltaCount;
    private int deltaIndex;

    private long lastFlagNanos;

    private double cycleMaxMs;
    private int minCycles;
    private double cadenceMaxCv;

    public CrystalCadence(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        cycleMaxMs = config.getDoubleElse("attack-patterns.crystal-cycle-max-ms", 150.0D);
        minCycles = Math.max(4, config.getIntElse("attack-patterns.crystal-min-cycles", 8));
        cadenceMaxCv = config.getDoubleElse("attack-patterns.crystal-cadence-max-cv", 0.6D);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            if (EntityTypes.isTypeInstanceOf(packet.getEntityType(), EntityTypes.END_CRYSTAL)) {
                Crystal crystal = new Crystal();
                Vector3d position = packet.getPosition();
                crystal.spawnNanos = System.nanoTime();
                crystal.x = position.x;
                crystal.y = position.y;
                crystal.z = position.z;
                crystals.put(packet.getEntityId(), crystal);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
                crystals.remove(id);
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

        Crystal crystal = crystals.remove(packet.getEntityId());
        if (crystal == null) {
            return;
        }

        double dx = crystal.x - player.x;
        double dz = crystal.z - player.z;
        if (dx * dx + dz * dz > 36.0 || Math.abs(crystal.y - player.y) > 6.0) {
            return;
        }

        long now = System.nanoTime();
        double deltaMs = (now - crystal.spawnNanos) / 1_000_000.0D;
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
                        "%d crystals broken %.1f ms (%.2f cv) after spawn", deltaCount, mean, cv));
                lastFlagNanos = now;
                deltaCount = 0;
                deltaIndex = 0;
            }
        }
    }
}
