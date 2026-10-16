package ac.aethel.aethelac.platform.api.manager.cloud;

import ac.aethel.aethelac.platform.api.command.PlayerSelector;
import ac.aethel.aethelac.platform.api.sender.Sender;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.SuggestionProvider;

public interface CloudPlatformCommandArguments {
    ParserDescriptor<Sender, PlayerSelector> singlePlayerSelectorParser();

    SuggestionProvider<Sender> onlinePlayerSuggestions();
}
