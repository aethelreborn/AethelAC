package ac.aethel.aethelac.platform.fabric.mixins;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.inject.FabricMinecraftServerHandle;
import ac.aethel.aethelac.platform.fabric.inject.FabricServerPlayerHandle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(MinecraftServer.class)
@Implements(@Interface(iface = FabricMinecraftServerHandle.class, prefix = "aethelac$", remap = Interface.Remap.NONE))
abstract class FabricIntermediaryServerMixin {

    public boolean aethelac$isPlayerOnline(UUID uuid) {
        return ((MinecraftServer) (Object) this).getPlayerList().getPlayer(uuid) != null;
    }

    public FabricServerPlayerHandle aethelac$playerByUuid(UUID uuid) {
        return (FabricServerPlayerHandle) ((MinecraftServer) (Object) this).getPlayerList().getPlayer(uuid);
    }

    public FabricServerPlayerHandle aethelac$playerByName(String name) {
        return (FabricServerPlayerHandle) ((MinecraftServer) (Object) this).getPlayerList().getPlayerByName(name);
    }

    @SuppressWarnings("unchecked")
    public Collection<FabricServerPlayerHandle> aethelac$onlinePlayers() {
        return (Collection<FabricServerPlayerHandle>) (Collection<?>) ((MinecraftServer) (Object) this).getPlayerList().getPlayers();
    }

    public Collection<UUID> aethelac$savedPlayerUuids() {
        PlayerDataStorage storage = ((MinecraftServer) (Object) this).playerDataStorage;
        String[] files = storage.playerDir.list((dir, name) -> name.endsWith(".dat"));
        Set<UUID> uuids = new HashSet<>();
        if (files == null) return uuids;

        for (String file : files) {
            try {
                uuids.add(UUID.fromString(file.substring(0, file.length() - 4)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    public int aethelac$getTickCount() {
        return ((MinecraftServer) (Object) this).getTickCount();
    }

    public String aethelac$getServerVersion() {
        return ((MinecraftServer) (Object) this).getServerVersion();
    }

    public Sender aethelac$createCommandSender() {
        return (Sender) (Object) ((MinecraftServer) (Object) this).createCommandSourceStack();
    }

    public boolean aethelac$usesAuthentication() {
        return ((MinecraftServer) (Object) this).usesAuthentication();
    }

    public boolean aethelac$isRunning() {
        return ((MinecraftServer) (Object) this).isRunning();
    }

    public int aethelac$getPlayerCount() {
        return ((MinecraftServer) (Object) this).getPlayerCount();
    }
}
