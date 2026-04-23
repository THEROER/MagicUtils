package dev.ua.theroer.magicutils.platform.bukkit;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
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
        if (BUKKIT_IS_GLOBAL_TICK_THREAD != null) {
            Object global = invoke(BUKKIT_IS_GLOBAL_TICK_THREAD, null);
            if (Boolean.TRUE.equals(global)) {
                return true;
            }
        }
        return Bukkit.isPrimaryThread();
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
