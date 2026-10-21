package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import ac.aethel.aethelac.utils.anticheat.MessageUtil;
import ac.aethel.aethelac.utils.reflection.GeyserUtil;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects known cheat clients from the login handshake.
 *
 * <p>Three independent server-observable evidence channels, all of them
 * artefacts the client sends whether it wants to or not:</p>
 *
 * <ol>
 *   <li><b>Brand</b> - the {@code minecraft:brand} (or legacy {@code MC|Brand})
 *       payload. A client that names itself after a cheat family is declaring
 *       what it is. The brand is trivially spoofable, so this is the weakest
 *       of the three signals and is mostly a catch for blatant users.</li>
 *   <li><b>Registered plugin channels</b> - the {@code minecraft:register}
 *       (or legacy {@code REGISTER}) payload lists every channel the client
 *       registered. Cheat clients that ship a mod register channels under
 *       their own namespace, so both the full channel name and the namespace
 *       in front of the colon are matched.</li>
 *   <li><b>Forge/FML handshake mod ids</b> - a modded client that volunteers
 *       its handshake (legacy {@code FML|HS}, modern {@code fml:handshake},
 *       {@code fml:login}, {@code forge:handshake}, {@code forge:login})
 *       leaks its mod ids. The ids are matched exactly, because a mod id is a
 *       precise token while a brand is free text.</li>
 * </ol>
 *
 * <p>Independent anticheats read the same three channels for the same
 * purpose (Allium matches cheat namespaces out of the register list, the
 * smp-network signatures list matches both brands and channel prefixes,
 * PACC and Windfall match the brand, Guardian names the client from the
 * brand). The lists shipped here are the union of those published sets,
 * trimmed of names that legitimate clients also use (Sodium, Hanabi,
 * OptiFine, Iris, Lunar, Badlion, Prism and other launcher or mod names).</p>
 *
 * <p>Note what this check cannot do: a ghost client that spoofs its brand to
 * {@code vanilla} and registers no channel leaves nothing here, which is why
 * the behavioural tiers (attack without arm swing, timer drift, bad packets)
 * stay the real teeth against those users.</p>
 *
 * <p>Every distinct match flags once per session. Bedrock (Geyser /
 * Floodgate) players are exempt - their handshake semantics differ.</p>
 */
@CheckData(name = "CheatClient", stableKey = "misc.cheat_client",
        description = "Joined with a known cheat client (brand, channel or mod id)", experimental = false)
public class CheatClient extends Check implements PacketReceiveListener {

    private static final AtomicBoolean GEYSER_FAILURE_LOGGED = new AtomicBoolean();
    private static final String REGISTER_CHANNEL_MODERN = "minecraft:register";
    private static final String REGISTER_CHANNEL_LEGACY = "REGISTER";
    private static final String BRAND_CHANNEL_MODERN = "minecraft:brand";
    private static final String BRAND_CHANNEL_LEGACY = "MC|Brand";

    /**
     * Handshake channels a modded client may volunteer. The server never
     * asks for a handshake (that risks disconnecting a Forge client with a
     * malformed payload), so a client that does not volunteer one is simply
     * not covered by the mod-id dimension.
     */
    private static final List<String> HANDSHAKE_CHANNELS = List.of(
            "fml|hs", "fml:handshake", "fml:login", "forge:handshake", "forge:login"
    );

    /**
     * Brands that only cheat families use. Words that legitimate clients,
     * launchers or mods also carry (sodium, hanabi, optifine, iris, lunar,
     * badlion, prism, forge, fabric, quilt, neoforge, geyser, vanilla) are
     * deliberately absent.
     */
    private static final List<String> DEFAULT_BRANDS = List.of(
            "vape", "prestige", "krypton", "myau", "liquidbounce", "meteor", "wurst",
            "aristois", "salhack", "konas", "novoline", "astolfo", "bleachhack",
            "inertia", "rusherhack", "phobos", "fdp", "nucleus", "koks", "ghostly",
            "exhibiton", "blexware", "rageware", "pandaware", "asteria",
            "aethelclient", "tru3club"
    );

    /**
     * Channel names and namespaces. Modded cheat clients register their
     * channels under a namespace that carries the family name, and injected
     * loaders (Vape) register a channel of their own.
     */
    private static final List<String> DEFAULT_CHANNELS = List.of(
            "vape", "vapeclient", "prestige", "krypton", "myau", "liquidbounce",
            "meteorclient", "meteor-client", "meteor:", "wurst:", "wurstclient",
            "impact:", "impactclient", "aristois", "sigma:", "konas:", "konasclient",
            "inertia:", "rusherhack", "future:", "rageware", "blc:cheat", "nucleus",
            "phobos", "fdp", "koks", "ghostly", "exhibiton", "blexware", "xplus",
            "tenacity", "nextgen", "crescent", "pyro", "xenon", "hellion",
            "salhack", "novoline", "bleachhack", "aethelclient", "tru3club"
    );

    /**
     * Mod ids as they appear in a Forge handshake. Mod ids are exact tokens,
     * so the words that are risky in free text are safe here.
     */
    private static final List<String> DEFAULT_MOD_IDS = List.of(
            "meteor-client", "meteorclient", "liquidbounce", "wurst", "impact",
            "aristois", "sigma", "salhack", "konas", "fdp", "nucleus", "inertia",
            "rusherhack", "phobos", "blexware", "rise", "koks", "prestige", "vape",
            "vapeclient", "rageware", "pyro", "crescent", "tenacity", "xplus",
            "nextgen", "xenon", "argon", "hellion", "asteria", "pandaware",
            "myau", "krypton", "bleachhack", "novoline", "aethelclient", "tru3club"
    );

    private final Set<String> flagged = new HashSet<>();

    private List<String> brandPatterns;
    private List<String> channelPatterns;
    private List<String> modIdPatterns;
    private Boolean bedrock;

    public CheatClient(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        brandPatterns = normalize(config.getStringListElse("cheat-client.brands", DEFAULT_BRANDS));
        channelPatterns = normalize(config.getStringListElse("cheat-client.channels", DEFAULT_CHANNELS));
        modIdPatterns = normalize(config.getStringListElse("cheat-client.mod-ids", DEFAULT_MOD_IDS));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (bedrock()) {
            return;
        }

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
            return;
        }

        if (HANDSHAKE_CHANNELS.contains(channel.toLowerCase(Locale.ROOT))) {
            if (data.length > 8192) {
                return;
            }
            // The legacy handshake is null separated text, the modern one is a
            // varint framed binary blob, so scan for maximal printable runs
            // instead of splitting: mod ids are plain [a-z0-9_.-] tokens in
            // both encodings and the framing bytes break the runs around them.
            for (String token : printableRuns(data)) {
                evaluateModId(token);
            }
        }
    }

    private static List<String> printableRuns(byte[] data) {
        List<String> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (byte raw : data) {
            char c = (char) (raw & 0xFF);
            boolean tokenChar = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            if (tokenChar) {
                current.append(c);
            } else if (current.length() > 0) {
                if (current.length() >= 3 && current.length() <= 63) {
                    runs.add(current.toString());
                }
                current.setLength(0);
            }
        }
        if (current.length() >= 3 && current.length() <= 63) {
            runs.add(current.toString());
        }
        return runs;
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
        String namespace = lower;
        int colon = lower.indexOf(':');
        if (colon > 0) {
            namespace = lower.substring(0, colon);
        }

        for (String pattern : channelPatterns) {
            // A namespace is a prefix of the full channel, so a substring
            // match on the channel already covers the namespace case; the
            // namespace is only reported so staff can see what leaked.
            if (!pattern.isEmpty() && lower.contains(pattern) && flagged.add("channel:" + pattern)) {
                flag("channel=" + channel + " namespace=" + namespace + " matched=" + pattern);
            }
        }
    }

    private void evaluateModId(String modId) {
        if (modIdPatterns == null) {
            return;
        }
        String lower = modId.toLowerCase(Locale.ROOT);
        for (String pattern : modIdPatterns) {
            if (!pattern.isEmpty() && lower.equals(pattern) && flagged.add("mod:" + pattern)) {
                flag("mod=" + modId + " matched=" + pattern);
            }
        }
    }

    private boolean bedrock() {
        if (bedrock == null) {
            try {
                bedrock = GeyserUtil.isBedrockPlayer(player.uuid);
            } catch (RuntimeException | LinkageError exception) {
                bedrock = true;
                if (GEYSER_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LogUtil.warn("Could not determine Bedrock status; CheatClient will skip affected players",
                            exception);
                }
            }
        }
        return bedrock;
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
