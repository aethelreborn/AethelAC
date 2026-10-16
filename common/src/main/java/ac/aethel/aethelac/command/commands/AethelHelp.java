package ac.aethel.aethelac.command.commands;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.command.BuildableCommand;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.utils.anticheat.MessageUtil;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

public class AethelHelp implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("aethel", "aethelac")
                        .literal("help", Description.of("Display help information"))
                        .permission("aethel.help")
                        .handler(this::handleHelp)
        );
    }

    private void handleHelp(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();

        for (String string : AethelAPI.INSTANCE.getConfigManager().getConfig().getStringList("help")) {
            if (string == null) continue;
            string = MessageUtil.replacePlaceholders(sender, string);
            sender.sendMessage(MessageUtil.miniMessage(string));
        }
    }
}
