package ac.aethel.aethelac.platform.fabric.mc1194;

import ac.aethel.aethelac.platform.fabric.AbstractFabricPlatformServer;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1171.AethelACFabric1170LoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1171.player.Fabric1170PlatformPlayer;
import ac.aethel.aethelac.platform.fabric.mc1194.convert.Fabric1190MessageUtil;
import ac.aethel.aethelac.platform.fabric.mc1194.entity.Fabric1194AethelEntity;
import ac.aethel.aethelac.platform.fabric.mc1194.player.Fabric1193PlatformInventory;
import ac.aethel.aethelac.platform.fabric.mc1161.player.Fabric1161PlatformInventory;
import ac.aethel.aethelac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.aethel.aethelac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.aethel.aethelac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.aethel.aethelac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;


public class AethelACFabric1190LoaderPlugin extends AethelACFabric1170LoaderPlugin {

    public AethelACFabric1190LoaderPlugin() {
        this(
            AethelACFabricIntermediaryLoaderPlugin::createCommandArguments,
            new FabricPlatformPlayerFactory(
                    Fabric1170PlatformPlayer::new,
                    Fabric1194AethelEntity::new,
                    PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_19_2)
                            ? Fabric1193PlatformInventory::new : Fabric1161PlatformInventory::new
            ),
            new Fabric1190PlatformServer(),
            new Fabric1190MessageUtil(),
            new Fabric1140ConversionUtil()
        );
    }

    protected AethelACFabric1190LoaderPlugin(
            LazyHolder<CloudPlatformCommandArguments> commandArguments,
            FabricPlatformPlayerFactory platformPlayerFactory,
            AbstractFabricPlatformServer platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil) {
        super(commandArguments, platformPlayerFactory, platformServer, fabricMessageUtil, fabricConversionUtil);
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_19_4;
    }
}
