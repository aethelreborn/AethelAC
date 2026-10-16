package ac.aethel.aethelac.utils.anticheat;

import ac.aethel.aethelac.AethelAPI;
import ac.grim.grimac.api.event.events.GrimJoinEvent;
import ac.grim.grimac.api.event.events.GrimQuitEvent;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.platform.api.player.PlatformPlayer;
import ac.aethel.aethelac.platform.api.player.PlatformPlayerCache;
import ac.aethel.aethelac.utils.reflection.GeyserUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    // Holder — PlayerDataManager is constructed inside AethelAPI's ctor, so a
    // plain static-final would see a null AethelAPI.INSTANCE. Holder init runs
    // on first fire, after AethelAPI is fully built.
    private static final class Channels {
        static final GrimJoinEvent.Channel JOIN = AethelAPI.INSTANCE.getEventBus().get(GrimJoinEvent.class);
        static final GrimQuitEvent.Channel QUIT = AethelAPI.INSTANCE.getEventBus().get(GrimQuitEvent.class);
    }

    private final Set<User> exemptUsers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<User, AethelPlayer> playerDataMap = new ConcurrentHashMap<>();

    public boolean isExemptUser(@Nullable User user) {
        return user != null && exemptUsers.contains(user);
    }

    public void exemptUser(@Nullable User user) {
        if (user == null) return;
        exemptUsers.add(user);
    }

    public boolean clearExemptions(@Nullable User user) {
        if (user == null) return false;
        return exemptUsers.remove(user);
    }

    @Nullable
    public AethelPlayer getPlayer(final @NotNull UUID uuid) {
        // Is it safe to interact with this, or is this internal PacketEvents code?
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(uuid);
        if (channel == null) return null;
        User user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        if (user == null) return null;
        return getPlayer(user);
    }

    @Nullable
    public AethelPlayer getPlayer(final @NotNull User user) {
        @Nullable AethelPlayer player = playerDataMap.get(user);
        if (player != null && player.platformPlayer != null && player.platformPlayer.isExternalPlayer())
            return null;
        return player;
    }

    public boolean shouldCheck(@NotNull User user) {
        if (isExemptUser(user)) return false;
        if (!ChannelHelper.isOpen(user.getChannel())) return false;

        if (user.getUUID() != null) {
            // Bedrock players don't have Java movement
            if (GeyserUtil.isBedrockPlayer(user.getUUID())) {
                exemptUser(user);
                return false;
            }

            // Has exempt permission
            AethelPlayer aethelPlayer = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if (aethelPlayer != null && aethelPlayer.hasPermission("aethel.exempt")) {
                exemptUser(user);
                return false;
            }

            // Geyser formatted player string
            // This will never happen for Java players, as the first character in the 3rd group is always 4 (xxxxxxxx-xxxx-4xxx-xxxx-xxxxxxxxxxxx)
            if (user.getUUID().toString().startsWith("00000000-0000-0000-0009")) {
                exemptUser(user);
                return false;
            }
        }

        return true;
    }

    public void addUser(final @NotNull User user) {
        if (shouldCheck(user)) {
            AethelPlayer player = new AethelPlayer(user);
            playerDataMap.put(user, player);
            Channels.JOIN.fire(player);
        }
    }

    public AethelPlayer remove(final @NotNull User user) {
        return playerDataMap.remove(user);
    }

    public void onDisconnect(User user) {
        AethelPlayer aethelPlayer = remove(user);
        if (aethelPlayer != null) Channels.QUIT.fire(aethelPlayer);
        clearExemptions(user);

        UUID uuid = user.getProfile().getUUID();

        // All cleanup paths should call onDisconnect; routing the session-close + toggle
        // eviction here means a stuck PE event (or a JVM-level channel
        // close that doesn't surface as UserDisconnectEvent) doesn't leak an open session.
        // hooks/toggles are NOOP when the datastore is disabled or its init failed
        // AND go NOOP mid-session if an operator runs /aethel reload after flipping database.enabled to false
        // a player who joined under the prior (enabled) config and disconnects post-reload has no live writer to fire onQuit, so their session stays open (row closed_at IS NULL).
        // The next datastore-enabled boot's crash sweep stamps closed_at = last_activity for still-open rows; permanently-disabled-after-the-fact leaves the row untouched until DB is enabled again.
        AethelAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks()
                .onQuitFromUserDisconnect(user, aethelPlayer, System.currentTimeMillis());
        if (uuid != null) {
            AethelAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore().evict(uuid);
        }

        // Check if calling async is safe
        if (uuid == null)
            return; // folia doesn't like null getPlayer()

        PlatformPlayer quittingPlayer = PlatformPlayerCache.getInstance().getPlayer(uuid);
        if (quittingPlayer != null) {
            AethelAPI.INSTANCE.getAlertManager().handlePlayerQuit(quittingPlayer);
        }

        AethelAPI.INSTANCE.getSpectateManager().onQuit(uuid);

        // TODO (Cross-platform) confirm this is 100% correct and will always remove players from cache when necessary
        AethelAPI.INSTANCE.getPlatformPlayerFactory().invalidatePlayer(uuid);
    }

    public Collection<AethelPlayer> getEntries() {
        return playerDataMap.values();
    }

    public int size() {
        return playerDataMap.size();
    }
}
