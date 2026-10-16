package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects no-swing aura modes: attacks fired with no arm animation behind
 * them.
 *
 * <p>Vanilla and every normal mod send an arm animation with each attack;
 * silent aura "no swing" modes strip the animation so the killaura is less
 * visible to observers. The server still sees attack packets that never
 * follow a swing, and a run of those is not producible by any normal
 * client.</p>
 */
@CheckData(name = "NoSwingAttack", stableKey = "aethel.combat.no_swing_attack",
        description = "Attacked without a recent arm animation", experimental = true)
public class NoSwingAttack extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long lastSwingNanos;
    private boolean sawSwing;
    private int misses;
    private long lastFlagNanos;

    private long swingMaxAgeNanos = 200_000_000L;
    private int missLimit = 4;

    public NoSwingAttack(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        swingMaxAgeNanos = Math.max(50, config.getIntElse("attack-patterns.swing-max-age-ms", 200)) * 1_000_000L;
        missLimit = Math.max(2, config.getIntElse("attack-patterns.swing-miss-attacks", 4));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION
                || event.getPacketType() == PacketType.Play.Client.PUNCH) {
            lastSwingNanos = System.nanoTime();
            sawSwing = true;
            misses = 0;
            return;
        }

        boolean attack;
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            attack = true;
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            attack = new WrapperPlayClientInteractEntity(event).getAction()
                    == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        } else {
            return;
        }
        if (!attack || !sawSwing) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastSwingNanos <= swingMaxAgeNanos) {
            misses = 0;
            return;
        }

        misses++;
        if (misses >= missLimit && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "%d attacks without a swing (last swing %.0fms ago)",
                    misses, (now - lastSwingNanos) / 1_000_000.0D));
            lastFlagNanos = now;
            misses = 0;
        }
    }
}
