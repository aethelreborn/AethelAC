package ac.aethel.aethelac.platform.bukkit;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.AethelExternalAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.aethel.aethelac.command.CloudCommandService;
import ac.grim.grimac.internal.platform.bukkit.resolver.BukkitResolverRegistrar;
import ac.aethel.aethelac.manager.init.Initable;
import ac.aethel.aethelac.manager.init.start.ExemptOnlinePlayersOnReload;
import ac.aethel.aethelac.manager.init.start.StartableInitable;
import ac.aethel.aethelac.platform.api.Platform;
import ac.aethel.aethelac.platform.api.PlatformLoader;
import ac.aethel.aethelac.platform.api.PlatformServer;
import ac.aethel.aethelac.platform.api.command.CommandService;
import ac.aethel.aethelac.platform.api.manager.ItemResetHandler;
import ac.aethel.aethelac.platform.api.manager.MessagePlaceHolderManager;
import ac.aethel.aethelac.platform.api.manager.PlatformPluginManager;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.player.PlatformPlayerFactory;
import ac.aethel.aethelac.platform.api.scheduler.PlatformScheduler;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.api.sender.SenderFactory;
import ac.aethel.aethelac.platform.bukkit.initables.BukkitBStats;
import ac.aethel.aethelac.platform.bukkit.initables.BukkitEventManager;
import ac.aethel.aethelac.platform.bukkit.initables.BukkitLuckPermsInitable;
import ac.aethel.aethelac.platform.bukkit.initables.BukkitTickEndEvent;
import ac.aethel.aethelac.platform.bukkit.manager.BukkitItemResetHandler;
import ac.aethel.aethelac.platform.bukkit.manager.BukkitMessagePlaceHolderManager;
import ac.aethel.aethelac.platform.bukkit.manager.BukkitCloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.bukkit.manager.BukkitPermissionRegistrationManager;
import ac.aethel.aethelac.platform.bukkit.manager.BukkitPlatformPluginManager;
import ac.aethel.aethelac.platform.bukkit.player.BukkitPlatformPlayerFactory;
import ac.aethel.aethelac.platform.bukkit.scheduler.bukkit.BukkitPlatformScheduler;
import ac.aethel.aethelac.platform.bukkit.scheduler.folia.FoliaPlatformScheduler;
import ac.aethel.aethelac.platform.bukkit.sender.BukkitSenderFactory;
import ac.aethel.aethelac.platform.bukkit.utils.placeholder.PlaceholderAPIExpansion;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import ac.aethel.aethelac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

public final class AethelACBukkitLoaderPlugin extends JavaPlugin implements PlatformLoader {
    public static AethelACBukkitLoaderPlugin LOADER;

    private final LazyHolder<PlatformScheduler> scheduler = LazyHolder.simple(this::createScheduler);
    private final LazyHolder<PacketEventsAPI<?>> packetEvents = LazyHolder.simple(() -> SpigotPacketEventsBuilder.build(this));
    private final LazyHolder<BukkitSenderFactory> senderFactory = LazyHolder.simple(BukkitSenderFactory::new);
    private final LazyHolder<ItemResetHandler> itemResetHandler = LazyHolder.simple(BukkitItemResetHandler::new);
    private final LazyHolder<CommandService> commandService = LazyHolder.simple(this::createCommandService);
    private final CloudPlatformCommandArguments commandArguments = new BukkitCloudPlatformCommandArguments();

    @Getter private final PlatformPlayerFactory platformPlayerFactory = new BukkitPlatformPlayerFactory();
    @Getter private final PlatformPluginManager pluginManager = new BukkitPlatformPluginManager();
    @Getter private final GrimPlugin plugin;
    @Getter private final PlatformServer platformServer = new BukkitPlatformServer();
    @Getter private final MessagePlaceHolderManager messagePlaceHolderManager = new BukkitMessagePlaceHolderManager();
    @Getter private final BukkitPermissionRegistrationManager permissionManager = new BukkitPermissionRegistrationManager();

    public AethelACBukkitLoaderPlugin() {
        BukkitResolverRegistrar registrar = new BukkitResolverRegistrar();
        registrar.registerAll(AethelAPI.INSTANCE.getExtensionManager());
        this.plugin = registrar.resolvePlugin(this);
    }

    @Override
    public void onLoad() {
        LOADER = this;
        AethelAPI.INSTANCE.load(this, this.getBukkitInitTasks());
    }

    private Initable[] getBukkitInitTasks() {
        return new Initable[] {
                new ExemptOnlinePlayersOnReload(),
                new BukkitEventManager(),
                new BukkitTickEndEvent(),
                new BukkitBStats(),
                new BukkitLuckPermsInitable(),
                (StartableInitable) () -> {
                    if (BukkitMessagePlaceHolderManager.hasPlaceholderAPI) {
                        new PlaceholderAPIExpansion().register();
                    }
                }
        };
    }

    @Override
    public void onEnable() {
        AethelAPI.INSTANCE.start();
    }

    @Override
    public void onDisable() {
        AethelAPI.INSTANCE.stop();
    }

    @Override
    public PlatformScheduler getScheduler() {
        return scheduler.get();
    }

    @Override
    public PacketEventsAPI<?> getPacketEvents() {
        return packetEvents.get();
    }

    @Override
    public ItemResetHandler getItemResetHandler() {
        return itemResetHandler.get();
    }

    @Override
    public CommandService getCommandService() {
        return commandService.get();
    }

    @Override
    public SenderFactory<CommandSender> getSenderFactory() {
        return senderFactory.get();
    }

    @Override
    @SuppressWarnings("removal")
    public void registerAPIService() {
        final AethelExternalAPI externalAPI = AethelAPI.INSTANCE.getExternalAPI();
        final EventBus eventBus = externalAPI.getEventBus();
        final ac.grim.grimac.api.plugin.GrimPlugin plugin = AethelAPI.INSTANCE.getGrimPlugin();

        // Bridge Aethel events → legacy Bukkit Event API so pre-1.3 plugins that
        // listened for ac.grim.grimac.api.events.* Bukkit events keep working.
        // Typed channel subscriptions here are plugin-bound so they go away if
        // AethelAC itself is disabled.

        eventBus.get(ac.grim.grimac.api.event.events.GrimJoinEvent.class).onJoin(plugin, (user) -> {
            Bukkit.getPluginManager().callEvent(new ac.grim.grimac.api.events.GrimJoinEvent(user));
        });

        eventBus.get(ac.grim.grimac.api.event.events.GrimQuitEvent.class).onQuit(plugin, (user) -> {
            Bukkit.getPluginManager().callEvent(new ac.grim.grimac.api.events.GrimQuitEvent(user));
        });

        eventBus.get(ac.grim.grimac.api.event.events.GrimReloadEvent.class).onReload(plugin, (success) -> {
            Bukkit.getPluginManager().callEvent(new ac.grim.grimac.api.events.GrimReloadEvent(success));
        });

        eventBus.subscribe(plugin, ac.grim.grimac.api.event.events.FlagEvent.class, event -> {
            ac.grim.grimac.api.events.FlagEvent bukkitEvent =
                    new ac.grim.grimac.api.events.FlagEvent(event.getUser(), event.getCheck(), event::getVerbose);
            Bukkit.getPluginManager().callEvent(bukkitEvent);
            event.setCancelled(event.isCancelled() || bukkitEvent.isCancelled());
        }, 0, false, AethelACBukkitLoaderPlugin.class);

        eventBus.get(ac.grim.grimac.api.event.events.CommandExecuteEvent.class).onCommandExecuteSupplier(plugin, (user, check, verbose, command, cancelled) -> {
            ac.grim.grimac.api.events.CommandExecuteEvent bukkitEvent =
                    new ac.grim.grimac.api.events.CommandExecuteEvent(user, check, verbose, command);
            Bukkit.getPluginManager().callEvent(bukkitEvent);
            return cancelled || bukkitEvent.isCancelled();
        });

        eventBus.get(ac.grim.grimac.api.event.events.CompletePredictionEvent.class).onCompletePrediction(plugin, (user, check, offset, cancelled) -> {
            // Legacy Bukkit event has a verbose field that the new channel event does not; pass empty.
            ac.grim.grimac.api.events.CompletePredictionEvent bukkitEvent =
                    new ac.grim.grimac.api.events.CompletePredictionEvent(user, check, "", offset);
            Bukkit.getPluginManager().callEvent(bukkitEvent);
            return cancelled || bukkitEvent.isCancelled();
        });

        GrimAPIProvider.init(externalAPI);
        Bukkit.getServicesManager().register(GrimAbstractAPI.class, externalAPI, this, ServicePriority.Normal);
    }

    private PlatformScheduler createScheduler() {
        return AethelAPI.INSTANCE.getPlatform() == Platform.FOLIA ? new FoliaPlatformScheduler() : new BukkitPlatformScheduler();
    }

    private CommandService createCommandService() {
        try {
            return new CloudCommandService(this::createCloudCommandManager, commandArguments);
        } catch (Throwable t) {
            LogUtil.warn("CRITICAL: Failed to initialize Command Framework. " +
                    "Aethel will continue to run with no commands.", t);
            return () -> {};
        }
    }

    private CommandManager<Sender> createCloudCommandManager() {
        LegacyPaperCommandManager<Sender> manager = new LegacyPaperCommandManager<>(
                this,
                ExecutionCoordinator.simpleCoordinator(),
                senderFactory.get()
        );
        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            try {
                manager.registerBrigadier();
                CloudBrigadierManager<Sender, ?> cbm = manager.brigadierManager();
                cbm.settings().set(BrigadierSetting.FORCE_EXECUTABLE, true);
            } catch (Throwable t) {
                LogUtil.error("Failed to register Brigadier native completions. Falling back to standard completions.", t);
            }
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
        return manager;
    }

    public BukkitSenderFactory getBukkitSenderFactory() {
        return senderFactory.get();
    }
}
