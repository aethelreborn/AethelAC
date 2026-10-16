package ac.aethel.aethelac.checks.impl.badpackets;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.player.AethelPlayer;

@CheckData(name = "BadPacketsB", stableKey = "aethel.badpackets.ignored_rotation", description = "Ignored set rotation packet")
public class BadPacketsB extends Check {
    public BadPacketsB(final AethelPlayer player) {
        super(player);
    }
}
