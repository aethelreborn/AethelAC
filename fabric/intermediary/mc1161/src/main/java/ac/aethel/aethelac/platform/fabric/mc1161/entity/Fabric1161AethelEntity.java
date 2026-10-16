package ac.aethel.aethelac.platform.fabric.mc1161.entity;

import ac.aethel.aethelac.platform.fabric.entity.AbstractFabricAethelEntity;
import ac.aethel.aethelac.platform.fabric.utils.thread.FabricFutureUtil;
import ac.aethel.aethelac.utils.math.Location;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Fabric1161AethelEntity extends AbstractFabricAethelEntity<Entity> {

    public Fabric1161AethelEntity(Entity entity) {
        super(entity);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location) {
        return FabricFutureUtil.supplySync(() -> {
            if (entity.getCommandSenderWorld() instanceof ServerLevel) {
                entity.teleportToWithTicket(
                        location.getX(),
                        location.getY(),
                        location.getZ()
                );
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean isDead() {
        return entity instanceof LivingEntity living ? living.isDeadOrDying() : entity.removed;
    }
}
