package ac.aethel.aethelac.platform.fabric.mc1171;

import ac.aethel.aethelac.platform.fabric.AbstractFabricPlatformServer;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import ac.aethel.aethelac.platform.fabric.mc1161.Fabric1140PlatformServer;
import ac.aethel.aethelac.platform.fabric.mc1161.player.Fabric1161PlatformInventory;
import ac.aethel.aethelac.platform.fabric.mc1171.entity.Fabric1170AethelEntity;
import ac.aethel.aethelac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.aethel.aethelac.platform.fabric.mc1161.util.convert.Fabric1161MessageUtil;
import ac.aethel.aethelac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.aethel.aethelac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.aethel.aethelac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;


public class AethelACFabric1170LoaderPlugin extends AethelACFabricIntermediaryLoaderPlugin {

    public AethelACFabric1170LoaderPlugin() {
        this(AethelACFabricIntermediaryLoaderPlugin::createCommandArguments,
                new FabricPlatformPlayerFactory(
                        Fabric1170PlatformPlayer::new,
                        Fabric1170AethelEntity::new,
                        Fabric1161PlatformInventory::new
                ),
                PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_17)
                        ? new Fabric1171PlatformServer() : new Fabric1140PlatformServer(),
                new Fabric1161MessageUtil(),
                new Fabric1140ConversionUtil()
        );
    }

    protected AethelACFabric1170LoaderPlugin(LazyHolder<CloudPlatformCommandArguments> commandArguments,
                                           FabricPlatformPlayerFactory playerFactory,
                                           AbstractFabricPlatformServer platformServer,
                                           IFabricMessageUtil fabricMessageUtil,
                                           IFabricConversionUtil fabricConversionUtil) {
        super(
                commandArguments,
                playerFactory,
                platformServer,
                fabricMessageUtil,
                fabricConversionUtil
        );
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_17_1;
    }
}
