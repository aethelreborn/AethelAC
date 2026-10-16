package ac.aethel.aethelac.platform.fabric.utils.thread;

import ac.aethel.aethelac.AethelAPI;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FabricFutureUtil {
    public static <U> CompletableFuture<U> supplySync(Supplier<U> entityTeleportSupplier) {
        CompletableFuture<U> ret = new CompletableFuture<>();
        AethelAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(AethelAPI.INSTANCE.getGrimPlugin(),
                () -> ret.complete(entityTeleportSupplier.get()));
        return ret;
    }
}
