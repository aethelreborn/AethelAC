package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientResourcePackStatus;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientResourcePackStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects resource-pack spoofing: a client that claims a pack applied at a
 * fixed delay without downloading it.
 *
 * <p>Server RP spoof modules answer the pack prompt and then send
 * {@code SUCCESSFULLY_LOADED} on a fixed timer (300 ms after acceptance)
 * regardless of pack size or network. A real client applies the pack when
 * the download and apply finish, which varies by hundreds of milliseconds,
 * so repeated fixed-gap success reports are not physical.</p>
 */
@CheckData(name = "PackSpoof", stableKey = "aethel.misc.pack_spoof",
        description = "Reported resource packs loaded at a fixed machine delay", experimental = true)
public class PackSpoof extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private long acceptNanos;
    private int fixedPairs;
    private long lastFlagNanos;

    private long windowMinNanos = 293_000_000L;
    private long windowMaxNanos = 360_000_000L;
    private int minPairs = 2;

    public PackSpoof(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowMinNanos = Math.max(1, config.getIntElse("pack-spoof.spoof-min-ms", 293)) * 1_000_000L;
        windowMaxNanos = Math.max(2, config.getIntElse("pack-spoof.spoof-max-ms", 360)) * 1_000_000L;
        minPairs = Math.max(2, config.getIntElse("pack-spoof.spoof-min-pairs", 2));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean accepted;
        boolean success;
        if (event.getPacketType() == PacketType.Play.Client.RESOURCE_PACK_STATUS) {
            WrapperPlayClientResourcePackStatus.Result result = new WrapperPlayClientResourcePackStatus(event).getResult();
            accepted = result == WrapperPlayClientResourcePackStatus.Result.ACCEPTED;
            success = result == WrapperPlayClientResourcePackStatus.Result.SUCCESSFULLY_LOADED;
        } else if (event.getPacketType() == PacketType.Configuration.Client.RESOURCE_PACK_STATUS) {
            WrapperConfigClientResourcePackStatus.Result result = new WrapperConfigClientResourcePackStatus(event).getResult();
            accepted = result == WrapperConfigClientResourcePackStatus.Result.ACCEPTED;
            success = result == WrapperConfigClientResourcePackStatus.Result.SUCCESSFULLY_LOADED;
        } else {
            return;
        }
        long now = System.nanoTime();
        if (accepted) {
            acceptNanos = now;
            return;
        }
        if (!success || acceptNanos == 0L) {
            return;
        }

        long gap = now - acceptNanos;
        acceptNanos = 0L;
        if (gap < windowMinNanos || gap > windowMaxNanos) {
            return;
        }

        fixedPairs++;
        if (fixedPairs >= minPairs && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT, "%d resource packs reported loaded %.0fms after acceptance",
                    fixedPairs, gap / 1_000_000.0D));
            lastFlagNanos = now;
        }
    }
}
