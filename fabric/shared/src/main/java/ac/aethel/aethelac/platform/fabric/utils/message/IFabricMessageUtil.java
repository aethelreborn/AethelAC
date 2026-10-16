package ac.aethel.aethelac.platform.fabric.utils.message;

import ac.aethel.aethelac.platform.api.sender.Sender;

public interface IFabricMessageUtil {
    Object textLiteral(String message);

    void sendMessage(Sender target, Object message, boolean overlay);
}
