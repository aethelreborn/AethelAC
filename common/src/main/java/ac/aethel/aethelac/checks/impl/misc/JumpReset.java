package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects automatic jump resets: a jump within ticks of every knockback.
 *
 * <p>Jump-reset modules spend the knockback tick budget deliberately - they
 * fire the jump input as close to the velocity packet as the client allows,
 * on every single hit, to cancel the opponent's combo. Humans do reset sprint
 * manually after some hits, but their reaction lands anywhere in a wide
 * organic band, not pinned to the first one-to-two ticks after each
 * knockback. The window is widened by the measured ping so latency never
 * causes a false positive, and the pattern needs repeated hits.</p>
 */
@CheckData(name = "JumpReset", stableKey = "aethel.misc.jump_reset",
        description = "Jumped within ticks of every knockback", experimental = true)
public class JumpReset extends Check implements PacketReceiveListener, PacketSendListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] ring = new long[8];
    private int index;

    private int lastSampledTick = -1;
    private boolean lastInput;
    private int knockbackTick = -1;

    private long lastFlagNanos;

    private int maxTicks;
    private int minEvents;
    private double counterSeconds;
    private int maxPingMs;

    public JumpReset(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxTicks = Math.max(1, config.getIntElse("jump-analysis.jumpreset-max-ticks", 2));
        minEvents = Math.max(2, config.getIntElse("jump-analysis.jumpreset-min-events", 5));
        counterSeconds = config.getDoubleElse("jump-analysis.jumpreset-counter-seconds", 60.0D);
        maxPingMs = config.getIntElse("attack-patterns.timing-max-ping-ms", 150);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_VELOCITY) {
            return;
        }
        if (player.getTransactionPing() > maxPingMs) {
            return;
        }

        WrapperPlayServerEntityVelocity velocity = new WrapperPlayServerEntityVelocity(event);
        if (velocity.getEntityId() != player.entityID) {
            return;
        }

        knockbackTick = AethelAPI.INSTANCE.getTickManager().currentTick;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isTickPacket(event.getPacketType()) || !player.cameraEntity.isSelf()) {
            lastSampledTick = -1;
            return;
        }

        int tick = AethelAPI.INSTANCE.getTickManager().currentTick;
        if (tick == lastSampledTick) {
            return;
        }
        lastSampledTick = tick;

        boolean jump = player.isJumping;
        if (jump && !lastInput) {
            if (knockbackTick >= 0 && tick >= knockbackTick) {
                int pingTicks = (int) Math.ceil(player.getTransactionPing() / 50.0D);
                int delta = tick - knockbackTick;
                if (delta <= maxTicks + pingTicks) {
                    recordEvent(System.nanoTime(), delta);
                }
            }
            knockbackTick = -1;
        }
        lastInput = jump;
    }

    private void recordEvent(long now, int deltaTicks) {
        ring[index] = now;
        index = (index + 1) % ring.length;

        int events = 0;
        for (long stamp : ring) {
            if (stamp != 0L && now - stamp <= (long) (counterSeconds * 1_000_000_000L)) {
                events++;
            }
        }

        if (events >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "%d jumps within %d ticks of knockback", events, deltaTicks));
            lastFlagNanos = now;
            Arrays.fill(ring, 0L);
        }
    }
}
