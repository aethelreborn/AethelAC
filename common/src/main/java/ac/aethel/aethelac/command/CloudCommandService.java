package ac.aethel.aethelac.command;

import ac.aethel.aethelac.command.commands.*;
import ac.aethel.aethelac.command.handler.AethelCommandFailureHandler;
import ac.aethel.aethelac.platform.api.command.CommandService;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.sender.Sender;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.processors.requirements.RequirementApplicable;
import org.incendo.cloud.processors.requirements.RequirementApplicable.RequirementApplicableFactory;
import org.incendo.cloud.processors.requirements.RequirementPostprocessor;
import org.incendo.cloud.processors.requirements.Requirements;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

public class CloudCommandService implements CommandService {

    public static final CloudKey<Requirements<Sender, SenderRequirement>> REQUIREMENT_KEY
            = CloudKey.of("requirements", new TypeToken<>() {});

    public static final RequirementApplicableFactory<Sender, SenderRequirement> REQUIREMENT_FACTORY
            = RequirementApplicable.factory(REQUIREMENT_KEY);

    private boolean commandsRegistered = false;

    private final Supplier<CommandManager<Sender>> commandManagerSupplier;
    private final CloudPlatformCommandArguments commandArguments;

    public CloudCommandService(Supplier<CommandManager<Sender>> commandManagerSupplier, CloudPlatformCommandArguments commandArguments) {
        this.commandManagerSupplier = commandManagerSupplier;
        this.commandArguments = commandArguments;
    }

    public void registerCommands() {
        if (commandsRegistered) return;
        CommandManager<Sender> commandManager = commandManagerSupplier.get();
        new AethelPerf().register(commandManager, commandArguments);
        new AethelDebug().register(commandManager, commandArguments);
        new AethelAlerts().register(commandManager, commandArguments);
        new AethelProfile().register(commandManager, commandArguments);
        new AethelSendAlert().register(commandManager, commandArguments);
        new AethelHelp().register(commandManager, commandArguments);
        new AethelHistory().register(commandManager, commandArguments);
        new AethelHistoryMigrate().register(commandManager, commandArguments);
        new AethelHistoryCopy().register(commandManager, commandArguments);
        new AethelReload().register(commandManager, commandArguments);
        new AethelSpectate().register(commandManager, commandArguments);
        new AethelStopSpectating().register(commandManager, commandArguments);
        new AethelLog().register(commandManager, commandArguments);
        new AethelVerbose().register(commandManager, commandArguments);
        new AethelVersion().register(commandManager, commandArguments);
        new AethelDump().register(commandManager, commandArguments);
        new AethelBrands().register(commandManager, commandArguments);
        new AethelList().register(commandManager, commandArguments);
        new AethelTestWebhook().register(commandManager, commandArguments);

        final RequirementPostprocessor<Sender, SenderRequirement>
                senderRequirementPostprocessor = RequirementPostprocessor.of(
                REQUIREMENT_KEY,
                new AethelCommandFailureHandler()
        );
        commandManager.registerCommandPostProcessor(senderRequirementPostprocessor);
        registerInvalidSyntaxHandler(commandManager);
        commandsRegistered = true;
    }

    private void registerInvalidSyntaxHandler(CommandManager<Sender> commandManager) {
        commandManager.exceptionController().registerHandler(InvalidSyntaxException.class, context -> {
            Sender sender = context.context().sender();
            if (isHistoryInput(context.context().rawInput().input())) {
                sender.sendMessage(Component.text("Invalid history syntax.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Use: /aethel history <player> [page <N>]", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Use: /aethel history <player> session <N|latest> [page <N>] [-d] [-v]", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Tip: /aethel history <player> session shows filter and detail options.", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Use /aethel history player <player> ... for names that collide with history subcommands.", NamedTextColor.GRAY));
                return;
            }
            sender.sendMessage(Component.text(context.exception().correctSyntax(), NamedTextColor.RED));
        });
    }

    private static boolean isHistoryInput(String rawInput) {
        String input = rawInput.strip();
        if (input.startsWith("/")) input = input.substring(1).strip();
        String[] tokens = input.toLowerCase(Locale.ROOT).split("\\s+");
        return tokens.length >= 2
                && (tokens[0].equals("aethel") || tokens[0].equals("aethelac"))
                && (tokens[1].equals("history") || tokens[1].equals("hist"));
    }

    protected <E extends Exception> void registerExceptionHandler(CommandManager<Sender> commandManager, Class<E> ex, Function<E, ComponentLike> toComponent) {
        commandManager.exceptionController().registerHandler(ex,
                (c) -> c.context().sender().sendMessage(toComponent.apply(c.exception()).asComponent().colorIfAbsent(NamedTextColor.RED))
        );
    }
}
