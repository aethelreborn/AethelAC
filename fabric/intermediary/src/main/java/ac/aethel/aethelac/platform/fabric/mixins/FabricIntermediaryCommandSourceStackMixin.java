package ac.aethel.aethelac.platform.fabric.mixins;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.platform.api.player.PlatformPlayer;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.sender.FabricIntermediarySenderFactory;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(CommandSourceStack.class)
@Implements(@Interface(iface = Sender.class, prefix = "grim$"))
abstract class FabricIntermediaryCommandSourceStackMixin {

    @Unique
    private CommandSourceStack aethelac$self() {
        return (CommandSourceStack) (Object) this;
    }

    @Unique
    private FabricIntermediarySenderFactory aethelac$factory() {
        return AethelACFabricIntermediaryLoaderPlugin.LOADER.getFabricSenderFactory();
    }

    public UUID grim$getUniqueId() {
        return aethelac$factory().getUniqueId(aethelac$self());
    }

    public String grim$getName() {
        return aethelac$factory().getName(aethelac$self());
    }

    public void grim$sendMessage(String message) {
        aethelac$factory().sendMessage(aethelac$self(), message);
    }

    public void grim$sendMessage(Component message) {
        aethelac$factory().sendMessage(aethelac$self(), message);
    }

    public boolean grim$hasPermission(String permission) {
        return aethelac$factory().hasPermission(aethelac$self(), permission);
    }

    public boolean grim$hasPermission(String permission, boolean defaultIfUnset) {
        return aethelac$factory().hasPermission(aethelac$self(), permission, defaultIfUnset);
    }

    public void grim$performCommand(String commandLine) {
        aethelac$factory().performCommand(aethelac$self(), commandLine);
    }

    public boolean grim$isConsole() {
        return aethelac$factory().isConsole(aethelac$self());
    }

    public Object grim$getNativeSender() {
        return aethelac$self();
    }

    public @Nullable PlatformPlayer grim$getPlatformPlayer() {
        return AethelAPI.INSTANCE.getPlatformPlayerFactory().getFromUUID(grim$getUniqueId());
    }
}
