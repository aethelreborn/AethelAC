package ac.aethel.aethelac.checks.debug;

import ac.aethel.aethelac.checks.AethelProcessor;
import ac.aethel.aethelac.player.AethelPlayer;

public abstract class AbstractDebugHandler extends AethelProcessor {
    public AbstractDebugHandler(AethelPlayer player) {
        super(player);
    }

    public abstract void toggleListener(AethelPlayer player);

    public abstract boolean toggleConsoleOutput();
}
