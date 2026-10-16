package ac.aethel.aethelac.checks.impl.badpackets;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.player.AethelPlayer;

@CheckData(name = "BadPacketsN", stableKey = "aethel.badpackets.invalid_teleport", description = "Ignored or failed to accept a required server teleport")
public class BadPacketsN extends Check {
    public BadPacketsN(final AethelPlayer player) {
        super(player);
    }
}
