package dev.ua.theroer.magicutils.platform.bukkit;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility for handling cross-platform threading between standard Bukkit and Folia.
 */
public final class BukkitThreading {
    private static final boolean FOLIA = detectFolia();
    private static final Method BUKKIT_GET_GLOBAL_REGION_SCHEDULER = resolveBukkitMethod(
            "getGlobalRegionScheduler"
    );
    private static final Method BUKKIT_GET_REGION_SCHEDULER = resolveBukkitMethod(
            "getRegionScheduler"
    );
    private static final Method BUKKIT_IS_OWNED_BY_CURRENT_REGION_LOC = resolveBukkitMethod(
            "isOwnedByCurrentRegion",
            World.class,
            int.class,
            int.class
    );
    private static final Method REGION_SCHEDULER_EXECUTE = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.RegionScheduler",
            "execute",
            Plugin.class,
            World.class,
            int.class,
            int.class,
            Runnable.class
    );
    private static final Method REGION_SCHEDULER_RUN_DELAYED = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.RegionScheduler",
            "runDelayed",
            Plugin.class,
            World.class,
            int.class,
            int.class,
            java.util.function.Consumer.class,
            long.class
    );
    private static final Method BUKKIT_IS_GLOBAL_TICK_THREAD = resolveBukkitMethod(
            "isGlobalTickThread"
    );
    private static final Method BUKKIT_IS_OWNED_BY_CURRENT_REGION = resolveBukkitMethod(
            "isOwnedByCurrentRegion",
            Entity.class
    );
    private static final Method ENTITY_GET_SCHEDULER = resolveEntityMethod("getScheduler");
    private static final Method GLOBAL_SCHEDULER_EXECUTE = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            "execute",
            Plugin.class,
            Runnable.class
    );
    private static final Method ENTITY_SCHEDULER_EXECUTE = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.EntityScheduler",
            "execute",
            Plugin.class,
            Runnable.class,
            Runnable.class,
            long.class
    );
    private static final Method GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            "runAtFixedRate",
            Plugin.class,
            java.util.function.Consumer.class,
            long.class,
            long.class
    );
    private static final Method GLOBAL_SCHEDULER_RUN_DELAYED = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            "runDelayed",
            Plugin.class,
            java.util.function.Consumer.class,
            long.class
    );
    private static final Method SCHEDULED_TASK_CANCEL = resolveSchedulerMethod(
            "io.papermc.paper.threadedregions.scheduler.ScheduledTask",
            "cancel"
    );

    private BukkitThreading() {
    }

    /**
     * Checks if the current runtime is Folia (region-based threading).
     *
     * @return true if Folia, false otherwise
     */
    public static boolean isFoliaRuntime() {
        return FOLIA;
    }

    /**
     * Runs a task on the global main thread (or global region in Folia).
     *
     * @param plugin plugin instance
     * @param task task to run
     */
    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (plugin == null || task == null) {
            return;
        }
        if (!FOLIA) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return;
            }
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        if (isGlobalThread()) {
            task.run();
            return;
        }

        Object scheduler = invokeStatic(BUKKIT_GET_GLOBAL_REGION_SCHEDULER);
        invoke(
                requireMethod(GLOBAL_SCHEDULER_EXECUTE, "GlobalRegionScheduler.execute"),
                scheduler,
                plugin,
                task
        );
    }

    /**
     * A handle to a scheduled repeating or delayed task, so the caller can stop it
     * early. Cross-runtime: on Folia it wraps a {@code ScheduledTask}, on standard
     * Bukkit a {@code BukkitTask}. {@link #cancel()} is idempotent.
     */
    public interface Task {
        /** Cancels the task so it does not run again. Safe to call more than once. */
        void cancel();
    }

    /** A {@link Task} whose backing scheduler could not be reached — a no-op handle. */
    private static final Task NOOP_TASK = () -> {
    };

    /**
     * Runs {@code task} repeatedly on the global thread (or global region on Folia),
     * starting after {@code initialDelayTicks} and repeating every
     * {@code periodTicks}. Use this for step-based world animations (weather ramps,
     * countdowns) that must not touch a single region column — it is the Folia-safe
     * replacement for {@code BukkitRunnable.runTaskTimer}, which throws on Folia.
     *
     * <p>Delays are clamped to a minimum of one tick. The returned {@link Task}
     * cancels the repetition; callers that finish early (e.g. a transition that
     * reached its target) should call {@link Task#cancel()} to stop it.
     *
     * @param plugin           plugin instance
     * @param task             work to run each period
     * @param initialDelayTicks ticks before the first run (min 1)
     * @param periodTicks      ticks between runs (min 1)
     * @return a handle to cancel the repetition, or a no-op handle if scheduling failed
     */
    public static Task runGlobalRepeating(JavaPlugin plugin, Runnable task,
            long initialDelayTicks, long periodTicks) {
        if (plugin == null || task == null) {
            return NOOP_TASK;
        }
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (!FOLIA) {
            org.bukkit.scheduler.BukkitTask bukkitTask =
                    Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            return bukkitTask::cancel;
        }
        if (GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE == null) {
            return NOOP_TASK;
        }
        Object scheduler = invokeStatic(BUKKIT_GET_GLOBAL_REGION_SCHEDULER);
        java.util.function.Consumer<Object> consumer = ignored -> task.run();
        Object scheduled = invoke(GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE,
                scheduler, plugin, consumer, delay, period);
        return foliaTask(scheduled);
    }

    /**
     * Runs {@code task} once on the global thread (or global region on Folia) after
     * {@code delayTicks}. The Folia-safe replacement for
     * {@code BukkitScheduler.runTaskLater}. The delay is clamped to a minimum of one
     * tick.
     *
     * @param plugin     plugin instance
     * @param task       work to run
     * @param delayTicks ticks before the run (min 1)
     * @return a handle to cancel the pending run, or a no-op handle if scheduling failed
     */
    public static Task runGlobalDelayed(JavaPlugin plugin, Runnable task, long delayTicks) {
        if (plugin == null || task == null) {
            return NOOP_TASK;
        }
        long delay = Math.max(1L, delayTicks);
        if (!FOLIA) {
            org.bukkit.scheduler.BukkitTask bukkitTask =
                    Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            return bukkitTask::cancel;
        }
        if (GLOBAL_SCHEDULER_RUN_DELAYED == null) {
            return NOOP_TASK;
        }
        Object scheduler = invokeStatic(BUKKIT_GET_GLOBAL_REGION_SCHEDULER);
        java.util.function.Consumer<Object> consumer = ignored -> task.run();
        Object scheduled = invoke(GLOBAL_SCHEDULER_RUN_DELAYED, scheduler, plugin, consumer, delay);
        return foliaTask(scheduled);
    }

    /** Wraps a Folia {@code ScheduledTask} handle as a cancellable {@link Task}. */
    private static Task foliaTask(Object scheduledTask) {
        if (scheduledTask == null || SCHEDULED_TASK_CANCEL == null) {
            return NOOP_TASK;
        }
        return () -> invoke(SCHEDULED_TASK_CANCEL, scheduledTask);
    }

    /**
     * Runs a task on the region owning the given entity.
     * Falls back to global region if Folia is not present or entity is null.
     *
     * @param plugin plugin instance
     * @param entity entity to use for region targeting
     * @param task task to run
     */
    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (plugin == null || task == null) {
            return;
        }
        if (entity == null || !FOLIA) {
            runGlobal(plugin, task);
            return;
        }
        if (isOwnedByCurrentRegion(entity)) {
            task.run();
            return;
        }

        Object scheduler = invoke(
                requireMethod(ENTITY_GET_SCHEDULER, "Entity.getScheduler"),
                entity
        );
        Object scheduled = invoke(
                requireMethod(ENTITY_SCHEDULER_EXECUTE, "EntityScheduler.execute"),
                scheduler,
                plugin,
                task,
                null,
                1L
        );
        if (Boolean.TRUE.equals(scheduled)) {
            return;
        }
        runGlobal(plugin, task);
    }

    /**
     * Runs a task for the given command sender, targeting their region if they are an entity.
     *
     * @param plugin plugin instance
     * @param sender sender to target
     * @param task task to run
     */
    public static void runForSender(JavaPlugin plugin, CommandSender sender, Runnable task) {
        if (sender instanceof Entity entity) {
            runEntity(plugin, entity, task);
            return;
        }
        runGlobal(plugin, task);
    }

    /**
     * Teleports {@code entity} to {@code target} in a way that is safe on every runtime.
     * Always uses {@link Entity#teleportAsync(Location)} because Canvas/Folia (Minecraft 26.x)
     * forbids the synchronous {@link Entity#teleport(Location)} on <em>any</em> thread — even an
     * entity-scheduled one — throwing {@code UnsupportedOperationException: Must use teleportAsync
     * while in region threading}. The async variant exists on modern Paper and Folia and is safe on
     * both, so consumers never have to branch on the runtime.
     *
     * @param entity entity to teleport
     * @param target destination location
     * @return future completing with true when the teleport succeeded, or a future completed with
     *         false when {@code entity} or {@code target} is null
     */
    public static java.util.concurrent.CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        if (entity == null || target == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return entity.teleportAsync(target);
    }

    /**
     * Runs a task on the region owning {@code location}. On standard Bukkit this runs
     * on the primary thread; on Folia it targets the owning region so block/chunk
     * mutation at that position is safe.
     *
     * @param plugin plugin instance
     * @param location target location (its world and block coordinates are used)
     * @param task task to run
     */
    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        if (location == null) {
            return;
        }
        runRegion(plugin, location.getWorld(), location.getBlockX(), location.getBlockZ(), task);
    }

    /**
     * Runs a task on the region owning the block position {@code (blockX, blockZ)}
     * in {@code world}. On standard Bukkit this runs on the primary thread; on Folia
     * it targets the owning region so block/chunk mutation at that position is safe.
     * Falls back to the global region when the location scheduler is unavailable.
     *
     * @param plugin plugin instance
     * @param world world containing the target block
     * @param blockX block x coordinate
     * @param blockZ block z coordinate
     * @param task task to run
     */
    public static void runRegion(JavaPlugin plugin, World world, int blockX, int blockZ, Runnable task) {
        if (plugin == null || world == null || task == null) {
            return;
        }
        if (!FOLIA) {
            runGlobal(plugin, task);
            return;
        }
        if (isOwnedByCurrentRegion(world, blockX, blockZ)) {
            task.run();
            return;
        }
        if (BUKKIT_GET_REGION_SCHEDULER == null || REGION_SCHEDULER_EXECUTE == null) {
            runGlobal(plugin, task);
            return;
        }
        Object scheduler = invokeStatic(BUKKIT_GET_REGION_SCHEDULER);
        invoke(REGION_SCHEDULER_EXECUTE, scheduler, plugin, world, blockX >> 4, blockZ >> 4, task);
    }

    /**
     * Runs {@code task} once on the region owning {@code location} after
     * {@code delayTicks}. On standard Bukkit this is {@code runTaskLater} on the
     * primary thread; on Folia it schedules on the owning region so delayed
     * block/chunk mutation at that position is safe — the Folia-safe replacement
     * for a location-targeted {@code BukkitScheduler.runTaskLater}. The delay is
     * clamped to a minimum of one tick.
     *
     * @param plugin     plugin instance
     * @param location   target location (its world and block coordinates are used)
     * @param task       work to run
     * @param delayTicks ticks before the run (min 1)
     * @return a handle to cancel the pending run, or a no-op handle if scheduling failed
     */
    public static Task runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delayTicks) {
        if (location == null) {
            return NOOP_TASK;
        }
        return runRegionDelayed(plugin, location.getWorld(), location.getBlockX(), location.getBlockZ(),
                task, delayTicks);
    }

    /**
     * Runs {@code task} once on the region owning the block position
     * {@code (blockX, blockZ)} in {@code world} after {@code delayTicks}. On
     * standard Bukkit this is {@code runTaskLater} on the primary thread; on Folia
     * it schedules on the owning region. Falls back to the global region when the
     * location scheduler is unavailable. The delay is clamped to a minimum of one tick.
     *
     * @param plugin     plugin instance
     * @param world      world containing the target block
     * @param blockX     block x coordinate
     * @param blockZ     block z coordinate
     * @param task       work to run
     * @param delayTicks ticks before the run (min 1)
     * @return a handle to cancel the pending run, or a no-op handle if scheduling failed
     */
    public static Task runRegionDelayed(JavaPlugin plugin, World world, int blockX, int blockZ,
            Runnable task, long delayTicks) {
        if (plugin == null || world == null || task == null) {
            return NOOP_TASK;
        }
        long delay = Math.max(1L, delayTicks);
        if (!FOLIA) {
            org.bukkit.scheduler.BukkitTask bukkitTask =
                    Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            return bukkitTask::cancel;
        }
        if (BUKKIT_GET_REGION_SCHEDULER == null || REGION_SCHEDULER_RUN_DELAYED == null) {
            return runGlobalDelayed(plugin, task, delay);
        }
        Object scheduler = invokeStatic(BUKKIT_GET_REGION_SCHEDULER);
        java.util.function.Consumer<Object> consumer = ignored -> task.run();
        Object scheduled = invoke(REGION_SCHEDULER_RUN_DELAYED,
                scheduler, plugin, world, blockX >> 4, blockZ >> 4, consumer, delay);
        return foliaTask(scheduled);
    }

    /**
     * Checks whether the block position {@code (blockX, blockZ)} in {@code world} is
     * owned by the current region. On standard Bukkit this is the primary-thread probe.
     *
     * @param world world containing the target block
     * @param blockX block x coordinate
     * @param blockZ block z coordinate
     * @return true if the current thread may touch that position
     */
    public static boolean isOwnedByCurrentRegion(World world, int blockX, int blockZ) {
        if (!FOLIA) {
            return Bukkit.isPrimaryThread();
        }
        if (BUKKIT_IS_OWNED_BY_CURRENT_REGION_LOC == null) {
            return false;
        }
        Object owned = invoke(BUKKIT_IS_OWNED_BY_CURRENT_REGION_LOC, null, world, blockX, blockZ);
        return Boolean.TRUE.equals(owned);
    }

    /**
     * Checks if the given entity is owned by the current region.
     * Always returns true on standard Bukkit if on the primary thread.
     *
     * @param entity entity to check
     * @return true if owned by current region
     */
    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!FOLIA) {
            return Bukkit.isPrimaryThread();
        }
        if (BUKKIT_IS_OWNED_BY_CURRENT_REGION == null) {
            return false;
        }
        Object owned = invoke(BUKKIT_IS_OWNED_BY_CURRENT_REGION, null, entity);
        return Boolean.TRUE.equals(owned);
    }

    /**
     * Checks whether the current thread is already the Folia global tick thread.
     * Falls back to Bukkit's primary-thread probe when the dedicated Folia API is unavailable.
     *
     * @return true when the current thread can execute global tasks inline
     */
    public static boolean isGlobalThread() {
        if (!FOLIA) {
            return Bukkit.isPrimaryThread();
        }
        // On Folia, Bukkit.isPrimaryThread() returns true on ANY region tick thread
        // (entity/region schedulers included), not just the global tick thread. So it
        // must NOT be used as a fallback here: treating a region thread as "global"
        // makes runGlobal execute inline, and global-only calls like
        // World.setGameRule then throw "Cannot modify server settings off of the
        // global region". When the dedicated Folia probe is available, trust only it;
        // when it is missing, assume we are not on the global thread and dispatch
        // through the scheduler rather than risk an unsafe inline run.
        if (BUKKIT_IS_GLOBAL_TICK_THREAD != null) {
            Object global = invoke(BUKKIT_IS_GLOBAL_TICK_THREAD, null);
            return Boolean.TRUE.equals(global);
        }
        return false;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Method resolveBukkitMethod(String name, Class<?>... parameterTypes) {
        try {
            return Bukkit.class.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method resolveEntityMethod(String name, Class<?>... parameterTypes) {
        try {
            return Entity.class.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method resolveSchedulerMethod(
            String className,
            String name,
            Class<?>... parameterTypes
    ) {
        try {
            Class<?> type = Class.forName(className);
            return type.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method requireMethod(Method method, String label) {
        if (method != null) {
            return method;
        }
        throw new IllegalStateException("Folia runtime detected, but " + label + " is unavailable.");
    }

    private static Object invokeStatic(Method method) {
        return invoke(requireMethod(method, method != null ? method.getName() : "static method"), null);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to invoke " + method, error);
        }
    }
}
