package ac.aethel.aethelac.manager.init.start;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.command.commands.AethelVersion;

public class UpdateChecker implements StartableInitable {
    @Override
    public void start() {
        if (AethelAPI.INSTANCE.getConfigManager().getConfig().getBooleanElse("check-for-updates", true)) {
            AethelVersion.checkForUpdatesAsync(AethelAPI.INSTANCE.getPlatformServer().getConsoleSender());
        }
    }
}
