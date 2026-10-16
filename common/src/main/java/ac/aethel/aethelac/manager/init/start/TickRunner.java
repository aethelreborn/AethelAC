package ac.aethel.aethelac.manager.init.start;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.platform.api.Platform;
import ac.aethel.aethelac.utils.anticheat.LogUtil;

public class TickRunner implements StartableInitable {
    @Override
    public void start() {
        LogUtil.info("Registering tick schedulers...");

        if (AethelAPI.INSTANCE.getPlatform() == Platform.FOLIA) {
            AethelAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(AethelAPI.INSTANCE.getGrimPlugin(), () -> {
                AethelAPI.INSTANCE.getTickManager().tickSync();
                AethelAPI.INSTANCE.getTickManager().tickAsync();
            }, 1, 1);
        } else {
            AethelAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().runAtFixedRate(AethelAPI.INSTANCE.getGrimPlugin(), () -> AethelAPI.INSTANCE.getTickManager().tickSync(), 0, 1);
            AethelAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(AethelAPI.INSTANCE.getGrimPlugin(), () -> AethelAPI.INSTANCE.getTickManager().tickAsync(), 0, 1);
        }
    }
}
