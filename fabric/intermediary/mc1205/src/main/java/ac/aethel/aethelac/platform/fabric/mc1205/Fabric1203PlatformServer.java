package ac.aethel.aethelac.platform.fabric.mc1205;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1194.Fabric1190PlatformServer;
import net.minecraft.commands.CommandSourceStack;

public class Fabric1203PlatformServer extends Fabric1190PlatformServer {

    @Override
    public double getTPS() {
        return Math.min(1000.0 / AethelACFabricIntermediaryLoaderPlugin.FABRIC_SERVER.getCurrentSmoothedTickTime(), AethelACFabricIntermediaryLoaderPlugin.FABRIC_SERVER.tickRateManager().tickrate());
    }

    @Override
    public void dispatchCommand(Sender sender, String command) {
        CommandSourceStack commandSource = AethelACFabricIntermediaryLoaderPlugin.LOADER.getFabricSenderFactory().unwrap(sender);
        AethelACFabricIntermediaryLoaderPlugin.FABRIC_SERVER.getCommands().performPrefixedCommand(commandSource, command);
    }
}
