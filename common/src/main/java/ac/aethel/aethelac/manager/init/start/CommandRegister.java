package ac.aethel.aethelac.manager.init.start;

import ac.aethel.aethelac.platform.api.command.CommandService;
import ac.aethel.aethelac.utils.anticheat.LogUtil;

public record CommandRegister(CommandService service) implements StartableInitable {

    @Override
    public void start() {
        try {
            if (service != null) {
                service.registerCommands();
            }
        } catch (RuntimeException t) {
            // This is the ultimate safety net. If command registration fails, Aethel keeps running.
            LogUtil.error("Failed to register commands! Aethel will run without command support.", t);
        }
    }
}
