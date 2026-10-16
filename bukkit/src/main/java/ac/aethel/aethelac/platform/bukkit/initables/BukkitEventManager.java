package ac.aethel.aethelac.platform.bukkit.initables;

import ac.aethel.aethelac.manager.init.start.StartableInitable;
import ac.aethel.aethelac.platform.bukkit.AethelACBukkitLoaderPlugin;
import ac.aethel.aethelac.platform.bukkit.events.PistonEvent;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import org.bukkit.Bukkit;

public class BukkitEventManager implements StartableInitable {
    public void start() {
        LogUtil.info("Registering singular bukkit event... (PistonEvent)");

        Bukkit.getPluginManager().registerEvents(new PistonEvent(), AethelACBukkitLoaderPlugin.LOADER);
    }
}
