package ac.aethel.aethelac.platform.fabric.mc1161.util.convert;

import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.fabric.utils.message.IFabricMessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

public class Fabric1161MessageUtil implements IFabricMessageUtil {
    @Override
    public Object textLiteral(String message) {
        return new TextComponent(message);
    }

    @Override
    public void sendMessage(Sender target, Object message, boolean overlay) {
        ((CommandSourceStack) (Object) target).sendSuccess((Component) message, overlay);
    }
}
