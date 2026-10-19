package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.checks.type.RotationListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.RotationUpdate;
import ac.aethel.aethelac.utils.reflection.GeyserUtil;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects interactions and mouse rotation while a container is open
 * (invwalk / loot aura, GUI-rotation modules).
 *
 * <p>A vanilla client's open container GUI captures mouse input completely:
 * no attack, block placement or digging packet can be produced while the
 * window is up, and no rotation update can either - the mouse is a cursor,
 * not the camera. Cheat modules attacking and looting simultaneously leave
 * the container open for seconds, so any world interaction in that state is
 * impossible, and a rotation update with a meaningful delta in that state is
 * impossible input for any mouse-captured GUI - provided the client has had
 * time to see the opened window and never closed it.</p>
 *
 * <p>Both tiers share the same open-window tracking: the open age must
 * exceed the higher of the configured floor and twice the transaction ping
 * plus 100 ms, because rotations sent while the window was still travelling
 * to the client are legitimate. TCP ordering guarantees that any rotation
 * the client sends after closing the window arrives after its close packet,
 * so only the open side needs latency compensation. Bedrock (Geyser /
 * Floodgate) players are exempt - their GUI semantics differ - and the
 * usual teleport, vehicle, spectator and non-self-camera guards apply.</p>
 */
@CheckData(name = "InvWalk", stableKey = "aethel.misc.inv_walk",
        description = "Interacted or rotated while a container is open", experimental = true)
public class InvWalk extends Check implements PacketReceiveListener, PacketSendListener, RotationListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long containerOpenNanos;
    private int events;
    private long firstEventNanos;
    private long lastFlagNanos;

    private int rotationEvents;
    private double rotationDegrees;
    private long rotationFirstNanos;
    private Boolean bedrock;

    private long minOpenNanos;
    private int minEvents;
    private long counterNanos;
    private double rotationMinDeltaDeg;
    private int rotationMinEvents;
    private long rotationCounterNanos;

    public InvWalk(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minOpenNanos = Math.max(50, config.getIntElse("inventory-automation.invwalk-min-open-ms", 350)) * 1_000_000L;
        minEvents = Math.max(2, config.getIntElse("inventory-automation.invwalk-min-events", 2));
        counterNanos = Math.max(1, config.getIntElse("inventory-automation.invwalk-counter-seconds", 10)) * 1_000_000_000L;
        rotationMinDeltaDeg = Math.max(0.05D, config.getDoubleElse("inventory-automation.invwalk-rotation-min-delta-deg", 0.5D));
        rotationMinEvents = Math.max(2, config.getIntElse("inventory-automation.invwalk-rotation-min-events", 2));
        rotationCounterNanos = Math.max(1, config.getIntElse("inventory-automation.invwalk-rotation-counter-seconds", 10)) * 1_000_000_000L;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            containerOpenNanos = System.nanoTime();
        } else if (event.getPacketType() == PacketType.Play.Server.CLOSE_WINDOW) {
            containerOpenNanos = 0L;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            containerOpenNanos = 0L;
            return;
        }

        if (containerOpenNanos == 0L) {
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY
                && event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                && event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }

        long now = System.nanoTime();
        long requiredOpenNanos = Math.max(minOpenNanos, player.getTransactionPing() * 2L * 1_000_000L + 100_000_000L);
        if (now - containerOpenNanos < requiredOpenNanos) {
            return;
        }

        if (firstEventNanos == 0L || now - firstEventNanos > counterNanos) {
            events = 0;
            firstEventNanos = now;
        }
        events++;

        if (events >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "interacted with the world %.1f s after a container opened",
                    (now - containerOpenNanos) / 1_000_000_000.0D));
            lastFlagNanos = now;
            events = 0;
            firstEventNanos = 0L;
        }
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (containerOpenNanos == 0L
                || player.gamemode == GameMode.SPECTATOR
                || player.inVehicle()
                || !player.cameraEntity.isSelf()
                || player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.lastPacketWasOnePointSeventeenDuplicate) {
            return;
        }

        double delta = rotationUpdate.deltaYawABS() + rotationUpdate.deltaPitchABS();
        if (delta < rotationMinDeltaDeg) {
            return;
        }

        long now = System.nanoTime();
        long requiredOpenNanos = Math.max(minOpenNanos, player.getTransactionPing() * 2L * 1_000_000L + 100_000_000L);
        if (now - containerOpenNanos < requiredOpenNanos || bedrock()) {
            return;
        }

        if (rotationFirstNanos == 0L || now - rotationFirstNanos > rotationCounterNanos) {
            rotationEvents = 0;
            rotationDegrees = 0.0D;
            rotationFirstNanos = now;
        }
        rotationEvents++;
        rotationDegrees += delta;

        if (rotationEvents >= rotationMinEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "rotated %.1f deg across %d updates while a container was open",
                    rotationDegrees, rotationEvents));
            lastFlagNanos = now;
            rotationEvents = 0;
            rotationDegrees = 0.0D;
            rotationFirstNanos = 0L;
        }
    }

    private boolean bedrock() {
        if (bedrock == null) {
            boolean result = false;
            try {
                result = GeyserUtil.isBedrockPlayer(player.uuid);
            } catch (Throwable ignored) {
                // Geyser/Floodgate API absent or unhappy - treat as a Java player.
            }
            bedrock = result;
        }
        return bedrock;
    }
}
