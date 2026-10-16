package ac.aethel.aethelac.platform.api;

import ac.aethel.aethelac.platform.api.sender.Sender;

public interface PlatformServer {

    String getPlatformImplementationString();

    void dispatchCommand(Sender sender, String command);

    Sender getConsoleSender();

    void registerOutgoingPluginChannel(String name);

    double getTPS();
}
