package ac.aethel.aethelac.platform.fabric;

import ac.aethel.aethelac.platform.fabric.inject.FabricMinecraftServerHandle;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class AethelACFabricIntermediaryEntryPoint extends AbstractAethelACFabricEntryPoint<AethelACFabricIntermediaryLoaderPlugin> {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(FabricServerEvents::fireServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(FabricServerEvents::fireServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(FabricServerEvents::fireEndTick);
        initialize(
                "aethelMainLoad",
                AethelACFabricIntermediaryLoaderPlugin.class,
                false
        );
    }

    @Override
    protected void setPlatformLoader(AethelACFabricIntermediaryLoaderPlugin platformLoader) {
        AethelACFabricIntermediaryLoaderPlugin.LOADER = platformLoader;
    }

    @Override
    protected void setNativeServer(FabricMinecraftServerHandle server) {
        AethelACFabricIntermediaryLoaderPlugin.FABRIC_SERVER = (MinecraftServer) server;
    }
}
