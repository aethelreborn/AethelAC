package ac.aethel.aethelac.platform.fabric;

import ac.aethel.aethelac.platform.fabric.manager.FabricItemResetHandler;
import ac.aethel.aethelac.platform.fabric.manager.FabricCloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.fabric.manager.FabricPermissionRegistrationManager;
import ac.aethel.aethelac.platform.fabric.command.FabricPlayerSelectorParser;
import ac.aethel.aethelac.platform.fabric.player.FabricPlatformPlayerFactory;
import ac.aethel.aethelac.platform.fabric.scheduler.FabricPlatformScheduler;
import ac.aethel.aethelac.platform.fabric.sender.AbstractFabricSenderFactory;
import ac.aethel.aethelac.platform.fabric.sender.FabricOfficialSenderFactory;
import me.lucko.fabric.api.permissions.v0.Permissions;
import ac.aethel.aethelac.platform.fabric.utils.FabricOfficialPolymerHook;
import ac.aethel.aethelac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.aethel.aethelac.utils.lazy.LazyHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public abstract class AethelACFabricOfficialLoaderPlugin extends AbstractAethelACFabricLoaderPlugin<
        FabricPlatformPlayerFactory,
        AbstractFabricPlatformServer,
        FabricPlatformScheduler,
        FabricOfficialSenderFactory,
        FabricItemResetHandler,
        FabricCloudPlatformCommandArguments
        > {
    public static MinecraftServer FABRIC_SERVER;
    public static AethelACFabricOfficialLoaderPlugin LOADER;

    public AethelACFabricOfficialLoaderPlugin(
            FabricPlatformPlayerFactory playerFactory,
            AbstractFabricPlatformServer platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil
    ) {
        super(
                LazyHolder.simple(FabricPlatformScheduler::new),
                LazyHolder.simple(FabricOfficialSenderFactory::new),
                LazyHolder.simple(() -> new FabricItemResetHandler(fabricConversionUtil)),
                LazyHolder.simple(AethelACFabricOfficialLoaderPlugin::createCommandArguments),
                LazyHolder.simple(() -> new FabricPermissionRegistrationManager(
                        LOADER.getFabricSenderFactory(),
                        name -> {
                            if (AbstractFabricSenderFactory.HAS_PERMISSIONS_API) {
                                Permissions.check(FABRIC_SERVER.createCommandSourceStack(), name);
                            }
                        })),
                playerFactory,
                platformServer,
                fabricMessageUtil,
                fabricConversionUtil
        );
        FabricPlatformServices.configure(
                playerFactory::getPlatformInventory,
                playerFactory::getPlatformEntity,
                player -> FabricOfficialPolymerHook.createTranslator((ServerPlayer) player),
                fabricMessageUtil::textLiteral,
                platformServer::getProfileByName,
                fabricConversionUtil
        );
    }

    public FabricOfficialSenderFactory getFabricSenderFactory() {
        return senderFactory.get();
    }

    public static FabricCloudPlatformCommandArguments createCommandArguments() {
        return new FabricCloudPlatformCommandArguments(new FabricPlayerSelectorParser<>(
                selector -> LOADER.getFabricSenderFactory().wrap(selector.single().createCommandSourceStack()),
                selector -> selector.inputString()
        ));
    }

}
