package ac.aethel.aethelac.platform.fabric.mc1161;

import ac.aethel.aethelac.platform.fabric.AbstractFabricPlatformServer;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1161.entity.Fabric1161AethelEntity;
import ac.aethel.aethelac.platform.fabric.mc1161.player.Fabric1161PlatformInventory;
import ac.aethel.aethelac.platform.fabric.mc1161.player.Fabric1161PlatformPlayer;
import ac.aethel.aethelac.platform.fabric.mc1161.util.convert.Fabric1140ConversionUtil;
import ac.aethel.aethelac.platform.fabric.mc1161.util.convert.Fabric1161MessageUtil;
import ac.aethel.aethelac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.aethel.aethelac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

public class AethelACFabric1161LoaderPlugin extends AethelACFabricIntermediaryLoaderPlugin {

    public AethelACFabric1161LoaderPlugin() {
        this(
            new FabricPlatformPlayerFactory(
                Fabric1161PlatformPlayer::new,
                Fabric1161AethelEntity::new,
                Fabric1161PlatformInventory::new
            ),
            new Fabric1140PlatformServer(),
            new Fabric1161MessageUtil(),
            new Fabric1140ConversionUtil()
        );
    }

    protected AethelACFabric1161LoaderPlugin(
            FabricPlatformPlayerFactory playerFactory,
            AbstractFabricPlatformServer platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil
    ) {
        super(AethelACFabricIntermediaryLoaderPlugin::createCommandArguments,
            playerFactory,
            platformServer,
            fabricMessageUtil,
            fabricConversionUtil
        );
    }

    @Override
    public ServerVersion getNativeVersion() {
        return ServerVersion.V_1_16_1;
    }
}
