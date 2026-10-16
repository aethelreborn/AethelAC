package ac.aethel.aethelac.command.handler;

import ac.aethel.aethelac.command.SenderRequirement;
import ac.aethel.aethelac.platform.api.sender.Sender;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.processors.requirements.RequirementFailureHandler;
import org.jetbrains.annotations.NotNull;

public class AethelCommandFailureHandler implements RequirementFailureHandler<Sender, SenderRequirement> {
    @Override
    public void handleFailure(@NotNull CommandContext<Sender> context, @NotNull SenderRequirement requirement) {
        context.sender().sendMessage(requirement.errorMessage(context.sender()));
    }
}
