package ac.aethel.aethelac.command.commands;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.command.BuildableCommand;
import ac.aethel.aethelac.command.CloudCommandService;
import ac.aethel.aethelac.command.requirements.PlayerSenderRequirement;
import ac.aethel.aethelac.platform.api.command.PlayerSelector;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.player.PlatformPlayer;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AethelSpectate implements BuildableCommand {
    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        commandManager.command(
                commandManager.commandBuilder("aethel", "aethelac")
                        .literal("spectate")
                        .permission("aethel.spectate")
                        .required("target", arguments.singlePlayerSelectorParser())
                        .handler(this::handleSpectate)
                        .apply(CloudCommandService.REQUIREMENT_FACTORY.create(PlayerSenderRequirement.INSTANCE))
        );
    }

    private void handleSpectate(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        PlayerSelector targetSelectorResults = context.getOrDefault("target", null);
        if (targetSelectorResults == null) return;

        PlatformPlayer targetPlatformPlayer = targetSelectorResults.getSinglePlayer().getPlatformPlayer();

        if (targetPlatformPlayer != null && targetPlatformPlayer.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "cannot-run-on-self", "%prefix% &cYou cannot use this command on yourself!"));
            return;
        }

        if (targetPlatformPlayer != null && targetPlatformPlayer.isExternalPlayer()) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "player-not-this-server", "%prefix% &cThis player isn't on this server!"));
            return;
        }

        @NotNull PlatformPlayer platformPlayer = Objects.requireNonNull(sender.getPlatformPlayer(), "platformPlayer");

        // hide player from tab list
        if (AethelAPI.INSTANCE.getSpectateManager().enable(platformPlayer)) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "spectate-return", "<click:run_command:/aethel stopspectating><hover:show_text:\"/aethel stopspectating\">\n%prefix% &fClick here to return to previous location\n</hover></click>"));
        }

        platformPlayer.setGameMode(GameMode.SPECTATOR);
        platformPlayer.teleportAsync(Objects.requireNonNull(targetPlatformPlayer, "targetPlatformPlayer").getLocation());
    }
}
