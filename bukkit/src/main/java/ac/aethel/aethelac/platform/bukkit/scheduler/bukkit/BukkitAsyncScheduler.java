package ac.aethel.aethelac.platform.bukkit.scheduler.bukkit;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.aethel.aethelac.platform.api.scheduler.AsyncScheduler;
import ac.aethel.aethelac.platform.api.scheduler.PlatformScheduler;
import ac.aethel.aethelac.platform.api.scheduler.TaskHandle;
import ac.aethel.aethelac.platform.bukkit.AethelACBukkitLoaderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class BukkitAsyncScheduler implements AsyncScheduler {

    private final BukkitScheduler bukkitScheduler = Bukkit.getScheduler();

    @Override
    public TaskHandle runNow(@NotNull GrimPlugin plugin, @NotNull Runnable task) {
        return new BukkitTaskHandle(bukkitScheduler.runTaskAsynchronously(AethelACBukkitLoaderPlugin.LOADER, task));
    }

    @Override
    public TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull Runnable task, long delay, @NotNull TimeUnit timeUnit) {
        return new BukkitTaskHandle(bukkitScheduler.runTaskLaterAsynchronously(
                AethelACBukkitLoaderPlugin.LOADER,
                task,
                PlatformScheduler.convertTimeToTicks(delay, timeUnit)
        ));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Runnable task, long delay, long period, @NotNull TimeUnit timeUnit) {
        return new BukkitTaskHandle(bukkitScheduler.runTaskTimerAsynchronously(
                AethelACBukkitLoaderPlugin.LOADER,
                task,
                PlatformScheduler.convertTimeToTicks(delay, timeUnit),
                PlatformScheduler.convertTimeToTicks(period, timeUnit)
        ));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return new BukkitTaskHandle(bukkitScheduler.runTaskTimerAsynchronously(
                AethelACBukkitLoaderPlugin.LOADER,
                task,
                initialDelayTicks,
                periodTicks
        ));
    }

    @Override
    public void cancel(@NotNull GrimPlugin plugin) {
        bukkitScheduler.cancelTasks(AethelACBukkitLoaderPlugin.LOADER);
    }
}
