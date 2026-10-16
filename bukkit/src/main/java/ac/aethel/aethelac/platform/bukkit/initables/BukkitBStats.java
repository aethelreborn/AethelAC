package ac.aethel.aethelac.platform.bukkit.initables;

import ac.aethel.aethelac.manager.init.start.StartableInitable;
import ac.aethel.aethelac.platform.bukkit.AethelACBukkitLoaderPlugin;
import ac.aethel.aethelac.utils.anticheat.Constants;
import io.github.retrooper.packetevents.bstats.bukkit.Metrics;

public class BukkitBStats implements StartableInitable {
    @Override
    public void start() {
        if (Constants.BSTATS_PLUGIN_ID <= 0) return;
        try {
            new Metrics(AethelACBukkitLoaderPlugin.LOADER, Constants.BSTATS_PLUGIN_ID);
        } catch (Exception ignored) {}
    }
}
