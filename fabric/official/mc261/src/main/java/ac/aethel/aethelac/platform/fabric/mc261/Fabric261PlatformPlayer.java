package ac.aethel.aethelac.platform.fabric.mc261;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.AethelACFabricOfficialLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.player.AbstractFabricPlatformPlayer;
import ac.aethel.aethelac.platform.fabric.utils.convert.FabricOfficialConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.thread.FabricFutureUtil;
import ac.aethel.aethelac.utils.math.Location;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

public class Fabric261PlatformPlayer extends AbstractFabricPlatformPlayer<ServerPlayer> {
    public Fabric261PlatformPlayer(ServerPlayer player) {
        super(player);
    }

    @Override
    public Sender getSender() {
        return AethelACFabricOfficialLoaderPlugin.LOADER.getFabricSenderFactory().wrap(serverPlayer().createCommandSourceStack());
    }

    @Override
    public void kickPlayer(String textReason) {
        serverPlayer().connection.disconnect((Component) AethelACFabricOfficialLoaderPlugin.LOADER.getFabricMessageUtils().textLiteral(textReason));
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        serverPlayer().setGameMode(FabricOfficialConversionUtil.toFabricGameMode(gameMode));
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) {
        return FabricFutureUtil.supplySync(() -> {
            serverPlayer().teleportTo(
                    (ServerLevel) location.getWorld(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    EnumSet.noneOf(Relative.class),
                    location.getYaw(),
                    location.getPitch(),
                    true
            );
            return true;
        });
    }
}
