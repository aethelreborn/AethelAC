package ac.aethel.aethelac.platform.bukkit.scheduler.folia;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.aethel.aethelac.platform.api.entity.AethelEntity;
import ac.aethel.aethelac.platform.api.scheduler.EntityScheduler;
import ac.aethel.aethelac.platform.api.scheduler.TaskHandle;
import ac.aethel.aethelac.platform.bukkit.AethelACBukkitLoaderPlugin;
import ac.aethel.aethelac.platform.bukkit.entity.BukkitAethelEntity;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoliaEntityScheduler implements EntityScheduler {

    @Override
    public void execute(@NotNull AethelEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delay) {
        ((BukkitAethelEntity) entity).getBukkitEntity().getScheduler().execute(AethelACBukkitLoaderPlugin.LOADER, task, retired, delay);
    }

    @Override
    public TaskHandle run(@NotNull AethelEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired) {
        ScheduledTask scheduled = ((BukkitAethelEntity) entity).getBukkitEntity().getScheduler().run(
                AethelACBukkitLoaderPlugin.LOADER,
                ignored -> task.run(),
                retired
        );

        return scheduled == null ? null : new FoliaTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runDelayed(@NotNull AethelEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        ScheduledTask scheduled = ((BukkitAethelEntity) entity).getBukkitEntity().getScheduler().runDelayed(
                AethelACBukkitLoaderPlugin.LOADER,
                ignored -> task.run(),
                retired,
                delayTicks
        );

        return scheduled == null ? null : new FoliaTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull AethelEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        ScheduledTask scheduled = ((BukkitAethelEntity) entity).getBukkitEntity().getScheduler().runAtFixedRate(
                AethelACBukkitLoaderPlugin.LOADER,
                ignored -> task.run(),
                retired,
                initialDelayTicks,
                periodTicks
        );

        return scheduled == null ? null : new FoliaTaskHandle(scheduled);
    }
}
