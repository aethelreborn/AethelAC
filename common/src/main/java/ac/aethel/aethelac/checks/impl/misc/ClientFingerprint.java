package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.MessageUtil;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Detects known cheat-client fingerprints sent during the login handshake:
 * the client brand packet and the channels a modded client registers.
 *
 * <p>Fingerprints are matched case-insensitively as substrings against the
 * configurable lists in {@code client-fingerprint.brands} and
 * {@code client-fingerprint.channels}. Every distinct match flags once per
 * session, so joins with a known cheat client accumulate violations.</p>
 */
@CheckData(name = "Fingerprint", stableKey = "misc.client_fingerprint", description = "Joined with a known cheat client fingerprint", experimental = false)
public class ClientFingerprint extends Check implements PacketReceiveListener {

    private static final String REGISTER_CHANNEL_MODERN = "minecraft:register";
    private static final String REGISTER_CHANNEL_LEGACY = "REGISTER";
    private static final String BRAND_CHANNEL_MODERN = "minecraft:brand";
    private static final String BRAND_CHANNEL_LEGACY = "MC|Brand";

    private static final List<String> DEFAULT_BRANDS = List.of(
            "wurst", "meteor", "sigma", "impact", "future", "salhack", "konas",
            "novoline", "astolfo", "aristois", "bleachhack", "lambda",
            "aethelclient", "tru3club"
    );
    private static final List<String> DEFAULT_CHANNELS = List.of(
            "wurst:", "meteor-client:", "bleachhack:", "aethelclient", "tru3club",
            "impact", "future:", "konas", "novoline", "sigma", "salhack", "lambda"
    );

    @Getter
    private final Set<String> channels = new LinkedHashSet<>();
    private final Set<String> flagged = new HashSet<>();

    private List<String> brandPatterns;
    private List<String> channelPatterns;

    public ClientFingerprint(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        brandPatterns = normalize(config.getStringListElse("client-fingerprint.brands", DEFAULT_BRANDS));
        channelPatterns = normalize(config.getStringListElse("client-fingerprint.channels", DEFAULT_CHANNELS));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            handle(packet.getChannelName(), packet.getData());
        } else if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage packet = new WrapperConfigClientPluginMessage(event);
            handle(packet.getChannelName(), packet.getData());
        }
    }

    private void handle(String channel, byte[] data) {
        if (channel == null || data == null || data.length == 0) {
            return;
        }

        if (channel.equals(REGISTER_CHANNEL_MODERN) || channel.equals(REGISTER_CHANNEL_LEGACY)) {
            if (data.length > 4096) {
                return;
            }
            String registered = new String(data, StandardCharsets.UTF_8);
            for (String name : registered.split("\u0000")) {
                if (name.isEmpty()) {
                    continue;
                }
                channels.add(name);
                evaluateChannel(name);
            }
            return;
        }

        if (channel.equals(BRAND_CHANNEL_MODERN) || channel.equals(BRAND_CHANNEL_LEGACY)) {
            if (data.length > 64 || data.length == 1) {
                return;
            }
            byte[] minusLength = new byte[data.length - 1];
            System.arraycopy(data, 1, minusLength, 0, minusLength.length);
            String brand = MessageUtil.stripColor(new String(minusLength, StandardCharsets.UTF_8));
            evaluateBrand(brand);
        }
    }

    private void evaluateBrand(String brand) {
        if (brand.isEmpty() || brandPatterns == null) {
            return;
        }
        String lower = brand.toLowerCase(Locale.ROOT);
        for (String pattern : brandPatterns) {
            if (!pattern.isEmpty() && lower.contains(pattern) && flagged.add("brand:" + pattern)) {
                flag("brand=" + brand + " matched=" + pattern);
            }
        }
    }

    private void evaluateChannel(String channel) {
        if (channelPatterns == null) {
            return;
        }
        String lower = channel.toLowerCase(Locale.ROOT);
        for (String pattern : channelPatterns) {
            if (!pattern.isEmpty() && lower.contains(pattern) && flagged.add("channel:" + pattern)) {
                flag("channel=" + channel + " matched=" + pattern);
            }
        }
    }

    private static List<String> normalize(List<String> patterns) {
        List<String> normalized = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank()) {
                normalized.add(pattern.toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }
}
