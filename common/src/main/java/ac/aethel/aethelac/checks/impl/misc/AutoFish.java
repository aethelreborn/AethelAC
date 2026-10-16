package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects auto-fishing modules that reel within a fixed window of the bobber
 * splash sound.
 *
 * <p>Auto Fish modules react to the incoming splash sound in the same tick,
 * so the reel-use packet lands well inside a human's audio-visual reaction
 * time. Only splash effects near the player count, and any reel consumes the
 * pending bite so the mandatory recast cannot be double counted.</p>
 */
@CheckData(name = "AutoFish", stableKey = "aethel.misc.auto_fish",
        description = "Fishing reeled within a machine window of the bobber splash", experimental = true)
public class AutoFish extends Check implements PacketReceiveListener, PacketSendListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long lastBiteNanos;
    private int fastReels;
    private long lastFlagNanos;

    private double reactionMaxMs;
    private int minPairs;

    public AutoFish(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        reactionMaxMs = config.getDoubleElse("fishing-analysis.reaction-max-ms", 120.0D);
        minPairs = Math.max(1, config.getIntElse("fishing-analysis.reaction-min-pairs", 3));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SOUND_EFFECT) {
            return;
        }
        WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);
        Sound sound = wrapper.getSound();
        if (sound == null || sound.getSoundId() == null
                || !sound.getSoundId().toString().endsWith("bobber.splash")) {
            return;
        }
        Vector3i pos = wrapper.getEffectPosition();
        if (Math.abs(pos.getX() - player.x) > 6.0D || Math.abs(pos.getZ() - player.z) > 6.0D) {
            return;
        }
        lastBiteNanos = System.nanoTime();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM || lastBiteNanos == 0L) {
            return;
        }
        ItemStack held = player.inventory.getHeldItem();
        if (held == null || held.isEmpty() || !held.getType().getName().toString().endsWith(":fishing_rod")) {
            return;
        }

        long now = System.nanoTime();
        double reactionMs = (now - lastBiteNanos) / 1_000_000.0D;
        lastBiteNanos = 0L;

        if (reactionMs > reactionMaxMs) {
            return;
        }
        fastReels++;

        if (fastReels >= minPairs && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "%d reels within %.0fms of the bobber splash (last %.0fms)",
                    fastReels, reactionMaxMs, reactionMs));
            lastFlagNanos = now;
        }
    }
}
