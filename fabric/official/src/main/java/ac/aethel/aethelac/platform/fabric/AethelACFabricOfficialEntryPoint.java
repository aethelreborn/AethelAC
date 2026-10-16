package ac.aethel.aethelac.platform.fabric;

import ac.aethel.aethelac.platform.fabric.inject.FabricMinecraftServerHandle;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class AethelACFabricOfficialEntryPoint extends AbstractAethelACFabricEntryPoint<AethelACFabricOfficialLoaderPlugin> {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(FabricServerEvents::fireServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(FabricServerEvents::fireServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(FabricServerEvents::fireEndTick);
        initialize(
                "aethel26MainLoad",
                AethelACFabricOfficialLoaderPlugin.class,
                true
        );
    }

    @Override
    protected void setPlatformLoader(AethelACFabricOfficialLoaderPlugin platformLoader) {
        AethelACFabricOfficialLoaderPlugin.LOADER = platformLoader;
    }

    @Override
    protected void setNativeServer(FabricMinecraftServerHandle server) {
        AethelACFabricOfficialLoaderPlugin.FABRIC_SERVER = (MinecraftServer) (Object) server;
    }
}
