package ac.aethel.aethelac.platform.fabric.mc1194;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.AethelACFabricIntermediaryLoaderPlugin;
import ac.aethel.aethelac.platform.fabric.mc1171.Fabric1171PlatformServer;
import net.minecraft.commands.CommandSourceStack;

public class Fabric1190PlatformServer extends Fabric1171PlatformServer {
    @Override
    public void dispatchCommand(Sender sender, String command) {
        CommandSourceStack commandSource = AethelACFabricIntermediaryLoaderPlugin.LOADER.getFabricSenderFactory().unwrap(sender);
        AethelACFabricIntermediaryLoaderPlugin.FABRIC_SERVER.getCommands().performPrefixedCommand(commandSource, command);
    }
}
