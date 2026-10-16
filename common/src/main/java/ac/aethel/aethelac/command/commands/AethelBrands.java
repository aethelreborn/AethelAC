package ac.aethel.aethelac.command.commands;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.command.BuildableCommand;
import ac.aethel.aethelac.manager.AlertManagerImpl;
import ac.aethel.aethelac.manager.datastore.PlayerToggleStore;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.player.PlatformPlayer;
import ac.aethel.aethelac.platform.api.sender.Sender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AethelBrands implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("aethel", "aethelac")
                        .literal("brands", Description.of("Toggle brands for the sender"))
                        .permission("aethel.brand")
                        .handler(this::handleBrands)
        );
    }

    private void handleBrands(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        if (sender.isPlayer()) {
            PlatformPlayer player = Objects.requireNonNull(context.sender().getPlatformPlayer(), "player");
            AlertManagerImpl am = AethelAPI.INSTANCE.getAlertManager();
            boolean newState = !am.hasBrandsEnabled(player);
            am.setBrandsEnabled(player, newState, false);
            AethelAPI.INSTANCE.getDataStoreLifecycle().playerToggleStore()
                    .applyUserToggle(player.getUniqueId(), PlayerToggleStore.KEY_BRANDS, newState);
        } else if (sender.isConsole()) {
            AethelAPI.INSTANCE.getAlertManager().toggleConsoleBrands();
        }
    }
}
