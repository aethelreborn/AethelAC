package ac.aethel.aethelac.platform.fabric.mc1205.player;

import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Fabric1202PlatformPlayer extends Fabric1170PlatformPlayer {
    public Fabric1202PlatformPlayer(ServerPlayer player) {
        super(player);
    }

    @Override
    public void kickPlayer(String textReason) {
        serverPlayer().connection.disconnect((Component) AethelACFabricIntermediaryLoaderPlugin.LOADER.getFabricMessageUtils().textLiteral(textReason));
    }
}
