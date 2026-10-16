package ac.aethel.aethelac.platform.fabric.mc261;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class Fabric261MessageUtil implements IFabricMessageUtil {
    @Override
    public Object textLiteral(String message) {
        return Component.literal(message);
    }

    @Override
    public void sendMessage(Sender target, Object message, boolean overlay) {
        ((CommandSourceStack) (Object) target).sendSuccess(() -> (Component) message, overlay);
    }
}
