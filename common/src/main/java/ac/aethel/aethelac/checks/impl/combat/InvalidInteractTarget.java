package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.player.AethelPlayer;

@CheckData(name = "InvalidInteractTarget", stableKey = "aethel.badpackets.invalid_entity_target", description = "Interacted with non-existent entity", experimental = true)
public class InvalidInteractTarget extends Check {
    public InvalidInteractTarget(AethelPlayer player) {
        super(player);
    }
}
