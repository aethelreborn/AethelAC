package ac.aethel.aethelac.platform.fabric.mc1171.entity;

import ac.aethel.aethelac.platform.fabric.mc1161.entity.Fabric1161AethelEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Fabric1170AethelEntity extends Fabric1161AethelEntity {

    public Fabric1170AethelEntity(Entity entity) {
        super(entity);
    }

    @Override
    public boolean isDead() {
        return this.entity instanceof LivingEntity living ? living.isDeadOrDying() : this.entity.isRemoved();
    }
}
