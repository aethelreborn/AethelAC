package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.player.AethelPlayer;

@CheckData(name = "Hitboxes", stableKey = "aethel.combat.hitboxes", description = "Tried to hit an entity outside its valid hitbox")
public class Hitboxes extends Check {
    public Hitboxes(AethelPlayer player) {
        super(player);
    }
}
