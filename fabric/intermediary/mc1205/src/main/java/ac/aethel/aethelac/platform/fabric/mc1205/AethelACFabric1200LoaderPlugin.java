package ac.aethel.aethelac.platform.fabric.mc1205;

import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import ac.aethel.aethelac.platform.fabric.mc1194.Fabric1190PlatformServer;
import ac.aethel.aethelac.platform.fabric.mc1194.AethelACFabric1190LoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1194.player.Fabric1193PlatformInventory;
import ac.aethel.aethelac.platform.fabric.mc1205.convert.Fabric1200MessageUtil;
import ac.aethel.aethelac.platform.fabric.mc1205.convert.Fabric1205ConversionUtil;
import ac.aethel.aethelac.platform.fabric.mc1194.entity.Fabric1194AethelEntity;
import ac.aethel.aethelac.platform.fabric.mc1205.player.Fabric1202PlatformPlayer;
import ac.aethel.aethelac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.aethel.aethelac.platform.fabric.player.FabricPlatformPlayerFactory;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import io.github.retrooper.packetevents.factory.fabric.FabricPacketEventsAPI;

public class AethelACFabric1200LoaderPlugin extends AethelACFabric1190LoaderPlugin {

    public AethelACFabric1200LoaderPlugin() {
        super(
                AethelACFabricIntermediaryLoaderPlugin::createCommandArguments,
                new FabricPlatformPlayerFactory(
                        FabricPacketEventsAPI.getServerAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_20_1)
                                ? Fabric1202PlatformPlayer::new : Fabric1170PlatformPlayer::new,
                        Fabric1194AethelEntity::new,
                        Fabric1193PlatformInventory::new
                ),
                FabricPacketEventsAPI.getServerAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_20_2)
                        ? new Fabric1203PlatformServer() : new Fabric1190PlatformServer(),
                new Fabric1200MessageUtil(),
                FabricPacketEventsAPI.getServerAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_20_4)
                        ? new Fabric1205ConversionUtil() : new Fabric1140ConversionUtil()
        );
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_20_5;
    }
}
