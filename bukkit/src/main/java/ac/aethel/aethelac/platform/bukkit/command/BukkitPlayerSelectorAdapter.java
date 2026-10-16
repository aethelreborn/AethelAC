package ac.aethel.aethelac.platform.bukkit.command;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.platform.api.command.PlayerSelector;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.platform.bukkit.sender.BukkitSenderFactory;
import lombok.RequiredArgsConstructor;
import org.incendo.cloud.bukkit.data.SinglePlayerSelector;

import java.util.Collection;
import java.util.Collections;

@RequiredArgsConstructor
public class BukkitPlayerSelectorAdapter implements PlayerSelector {
    private final SinglePlayerSelector bukkitSelector;

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Sender getSinglePlayer() {
        return ((BukkitSenderFactory) AethelAPI.INSTANCE.getSenderFactory()).map(bukkitSelector.single());
    }

    @Override
    public Collection<Sender> getPlayers() {
        return Collections.singletonList(((BukkitSenderFactory) AethelAPI.INSTANCE.getSenderFactory()).map(bukkitSelector.single()));
    }

    @Override
    public String inputString() {
        return bukkitSelector.inputString();
    }
}
