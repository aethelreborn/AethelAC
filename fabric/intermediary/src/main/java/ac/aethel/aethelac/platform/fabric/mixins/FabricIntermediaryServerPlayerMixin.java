package ac.aethel.aethelac.platform.fabric.mixins;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

@Mixin(ServerPlayer.class)
@Implements(@Interface(iface = ac.aethel.aethelac.platform.fabric.inject.FabricServerPlayerHandle.class, prefix = "aethelac$"))
abstract class FabricIntermediaryServerPlayerMixin extends Player {

    public FabricIntermediaryServerPlayerMixin(Level level, BlockPos blockPos, GameProfile gameProfile) {
        super(level, blockPos, gameProfile);
    }

    public void aethelac$resyncSharedFlags() {
        this.getEntityData().getItem(DATA_SHARED_FLAGS_ID).setDirty(true);
        this.getEntityData().isDirty = true;
    }

    public boolean aethelac$isDead() {
        return this.isDeadOrDying();
    }

    public void aethelac$sendSystemText(Object nativeComponent) {
        this.displayClientMessage((Component) nativeComponent, false);
    }

    public boolean aethelac$isDisconnected() {
        return ((ServerPlayer) (Object) this).hasDisconnected();
    }

    public String aethelac$usernameString() {
        return this.getName().getString();
    }

    public void aethelac$broadcastInventoryChanges() {
        this.containerMenu.broadcastChanges();
    }

    public void aethelac$stopUsingItem() {
        this.stopUsingItem();
    }

    public boolean aethelac$isUsingItem() {
        return this.isUsingItem();
    }

    public double aethelac$posX() {
        return this.getX();
    }

    public double aethelac$posY() {
        return this.getY();
    }

    public double aethelac$posZ() {
        return this.getZ();
    }

    public UUID aethelac$uuid() {
        return this.getUUID();
    }

    public Object aethelac$vehicleEntity() {
        return this.getVehicle();
    }

    public Object aethelac$gameMode() {
        return ((ServerPlayer) (Object) this).gameMode.getGameModeForPlayer();
    }

    public Object aethelac$heldItemStack() {
        return this.inventory.getSelected();
    }

    public Object aethelac$inventoryItemAt(int slot) {
        return this.inventory.getItem(slot);
    }

    public Object aethelac$usedItemHand() {
        return this.getUsedItemHand();
    }

    public int aethelac$inventorySlotCount() {
        return this.inventory.getContainerSize();
    }
}
