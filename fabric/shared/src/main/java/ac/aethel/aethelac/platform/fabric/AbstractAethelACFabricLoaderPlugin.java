package ac.aethel.aethelac.platform.fabric;

import ac.aethel.aethelac.AethelAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.aethel.aethelac.command.CloudCommandService;
import ac.grim.grimac.internal.plugin.resolver.GrimExtensionManager;
import ac.aethel.aethelac.platform.api.PlatformLoader;
import ac.aethel.aethelac.platform.api.PlatformServer;
import ac.aethel.aethelac.platform.api.command.CommandService;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.manager.ItemResetHandler;
import ac.aethel.aethelac.platform.api.manager.MessagePlaceHolderManager;
import ac.aethel.aethelac.platform.api.manager.PermissionRegistrationManager;
import ac.aethel.aethelac.platform.api.manager.PlatformPluginManager;
import ac.aethel.aethelac.platform.api.player.PlatformPlayerFactory;
import ac.aethel.aethelac.platform.api.scheduler.PlatformScheduler;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.api.sender.SenderFactory;
import ac.aethel.aethelac.platform.fabric.manager.FabricMessagePlaceHolderManager;
import ac.aethel.aethelac.platform.fabric.manager.FabricPlatformPluginManager;
import ac.aethel.aethelac.platform.fabric.resolver.FabricResolverRegistrar;
import ac.aethel.aethelac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import ac.aethel.aethelac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import lombok.Getter;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.fabric.FabricServerCommandManager;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractAethelACFabricLoaderPlugin<
        P extends PlatformPlayerFactory,
        S extends PlatformServer,
        H extends PlatformScheduler,
        F extends SenderFactory<?>,
        I extends ItemResetHandler,
        A extends CloudPlatformCommandArguments
        > implements PlatformLoader {

    protected final LazyHolder<H> scheduler;
    protected final PacketEventsAPI<?> packetEvents = PacketEvents.getAPI();
    protected final LazyHolder<F> senderFactory;
    protected final LazyHolder<I> itemResetHandler;
    protected final LazyHolder<A> commandArguments;
    protected final LazyHolder<CommandService> commandService = LazyHolder.simple(this::createCommandService);
    protected final LazyHolder<? extends PermissionRegistrationManager> permissionManager;
    protected final GrimPlugin plugin;
    @Getter
    protected final PlatformPluginManager pluginManager = new FabricPlatformPluginManager();
    @Getter
    protected final MessagePlaceHolderManager messagePlaceHolderManager = new FabricMessagePlaceHolderManager();
    protected final P playerFactory;
    protected final S platformServer;
    protected final IFabricMessageUtil fabricMessageUtil;
    @Getter
    protected final IFabricConversionUtil fabricConversionUtil;

    protected AbstractAethelACFabricLoaderPlugin(
            LazyHolder<H> scheduler,
            LazyHolder<F> senderFactory,
            LazyHolder<I> itemResetHandler,
            LazyHolder<A> commandArguments,
            LazyHolder<? extends PermissionRegistrationManager> permissionManager,
            P playerFactory,
            S platformServer,
            IFabricMessageUtil fabricMessageUtil,
            IFabricConversionUtil fabricConversionUtil
    ) {
        this.scheduler = scheduler;
        this.senderFactory = senderFactory;
        this.itemResetHandler = itemResetHandler;
        this.commandArguments = commandArguments;
        this.permissionManager = permissionManager;
        this.playerFactory = playerFactory;
        this.platformServer = platformServer;
        this.fabricMessageUtil = fabricMessageUtil;
        this.fabricConversionUtil = fabricConversionUtil;

        FabricResolverRegistrar resolverRegistrar = new FabricResolverRegistrar();
        GrimExtensionManager extensionManager = AethelAPI.INSTANCE.getExtensionManager();
        resolverRegistrar.registerAll(extensionManager);
        plugin = extensionManager.getPlugin("AethelAC");
    }

    @Override
    public H getScheduler() {
        return scheduler.get();
    }

    @Override
    public PacketEventsAPI<?> getPacketEvents() {
        return packetEvents;
    }

    @Override
    public I getItemResetHandler() {
        return itemResetHandler.get();
    }

    @Override
    public CommandService getCommandService() {
        return commandService.get();
    }

    @Override
    public SenderFactory<?> getSenderFactory() {
        return senderFactory.get();
    }

    @Override
    public GrimPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void registerAPIService() {
        GrimAPIProvider.init(AethelAPI.INSTANCE.getExternalAPI());
    }

    @Override
    public PermissionRegistrationManager getPermissionManager() {
        return permissionManager.get();
    }

    @Override
    public P getPlatformPlayerFactory() {
        return playerFactory;
    }

    @Override
    public S getPlatformServer() {
        return platformServer;
    }

    public IFabricMessageUtil getFabricMessageUtils() {
        return fabricMessageUtil;
    }

    private CommandService createCommandService() {
        try {
            return createPlatformCommandService();
        } catch (Throwable t) {
            LogUtil.warn("IMPORTANT: Command Framework failed to load (Missing Cloud Library?). \n" +
                    "Aethel will run without commands enabled!");
            if (!(t instanceof NoClassDefFoundError)) {
                LogUtil.error(t);
            }
            return () -> {};
        }
    }

    protected CommandService createPlatformCommandService() {
        @SuppressWarnings({"rawtypes", "unchecked"})
        CommandManager<@NotNull Sender> manager = new FabricServerCommandManager(
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        );
        return new CloudCommandService(() -> manager, commandArguments.get());
    }

    public abstract ServerVersion getNativeVersion();
}
