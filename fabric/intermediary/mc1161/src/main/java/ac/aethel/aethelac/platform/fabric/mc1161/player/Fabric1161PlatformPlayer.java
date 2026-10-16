package ac.aethel.aethelac.platform.fabric.mc1161.player;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.player.AbstractFabricPlatformPlayer;
import ac.aethel.aethelac.platform.fabric.utils.convert.FabricIntermediaryConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.thread.FabricFutureUtil;
import ac.aethel.aethelac.utils.math.Location;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class Fabric1161PlatformPlayer extends AbstractFabricPlatformPlayer<ServerPlayer> {
    public Fabric1161PlatformPlayer(ServerPlayer player) {
        super(player);
    }

    @Override
    public Sender getSender() {
        return AethelACFabricIntermediaryLoaderPlugin.LOADER.getFabricSenderFactory().wrap(serverPlayer().createCommandSourceStack());
    }

    @Override
    public void kickPlayer(String textReason) {
        serverPlayer().connection.disconnect((Component) AethelACFabricIntermediaryLoaderPlugin.LOADER.getFabricMessageUtils().textLiteral(textReason));
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        serverPlayer().setGameMode(FabricIntermediaryConversionUtil.toFabricGameMode(gameMode));
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) {
        return FabricFutureUtil.supplySync(() -> {
            serverPlayer().teleportTo(
                    (ServerLevel) location.getWorld(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
            return true;
        });
    }
}
