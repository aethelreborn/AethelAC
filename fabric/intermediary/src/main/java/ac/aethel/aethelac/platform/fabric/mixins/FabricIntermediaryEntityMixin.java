package ac.aethel.aethelac.platform.fabric.mixins;

import ac.aethel.aethelac.platform.fabric.inject.FabricEntityHandle;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

@Mixin(Entity.class)
@Implements(@Interface(iface = FabricEntityHandle.class, prefix = "aethelac$"))
abstract class FabricIntermediaryEntityMixin {

    public UUID aethelac$fabricEntityUuid() {
        return ((Entity) (Object) this).getUUID();
    }

    public boolean aethelac$fabricEjectPassengers() {
        Entity entity = (Entity) (Object) this;
        if (entity.isVehicle()) {
            entity.ejectPassengers();
            return true;
        }
        return false;
    }

    public Object aethelac$fabricWorld() {
        return ((Entity) (Object) this).level;
    }

    public double aethelac$fabricPosX() {
        return ((Entity) (Object) this).getX();
    }

    public double aethelac$fabricPosY() {
        return ((Entity) (Object) this).getY();
    }

    public double aethelac$fabricPosZ() {
        return ((Entity) (Object) this).getZ();
    }

    public float aethelac$fabricYaw(float partialTick) {
        return ((Entity) (Object) this).getViewYRot(partialTick);
    }

    public float aethelac$fabricPitch(float partialTick) {
        return ((Entity) (Object) this).getViewXRot(partialTick);
    }
}
