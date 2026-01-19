package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.ConfigNamespaceProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Platform provider for NeoForge runtime.
 */
public class NeoForgePlatformProvider implements Platform, ConfigNamespaceProvider, ConfigFormatProvider, ShutdownHookRegistrar {
    private static final Set<Runnable> SHUTDOWN_HOOKS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean SHUTDOWN_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN_RAN = new AtomicBoolean(false);
    private static volatile PlatformLogger shutdownLogger;

    private final Supplier<MinecraftServer> serverSupplier;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final Path configDir;
    private final boolean useConfigNamespace;
    private final String configNamespace;
    private final TaskScheduler taskScheduler;

    /**
     * Create a NeoForge platform adapter using the current server lifecycle hooks.
     */
    public NeoForgePlatformProvider() {
        this(null, ServerLifecycleHooks::getCurrentServer, LoggerFactory.getLogger("MagicUtils-NeoForge"), resolveConfigDir());
    }

    /**
     * Create a NeoForge platform adapter with a default namespace.
     *
     * @param modId mod id used for config namespace fallback
     */
    public NeoForgePlatformProvider(String modId) {
        this(modId, ServerLifecycleHooks::getCurrentServer, LoggerFactory.getLogger("MagicUtils-NeoForge"), resolveConfigDir());
    }

    /**
     * Create a NeoForge platform adapter with an explicit server instance.
     *
     * @param server server instance
     */
    public NeoForgePlatformProvider(MinecraftServer server) {
        this(null, () -> server, LoggerFactory.getLogger("MagicUtils-NeoForge"), resolveConfigDir());
    }

    /**
     * Create a NeoForge platform adapter with an explicit server instance and mod id.
     *
     * @param modId mod id used for config namespace fallback
     * @param server server instance
     */
    public NeoForgePlatformProvider(String modId, MinecraftServer server) {
        this(modId, () -> server, LoggerFactory.getLogger("MagicUtils-NeoForge"), resolveConfigDir());
    }

    /**
     * Create a NeoForge platform adapter with a custom server supplier.
     *
     * @param serverSupplier server supplier
     */
    public NeoForgePlatformProvider(Supplier<MinecraftServer> serverSupplier) {
        this(null, serverSupplier, LoggerFactory.getLogger("MagicUtils-NeoForge"), resolveConfigDir());
    }

    /**
     * Create a NeoForge platform adapter with custom settings.
     *
     * @param modId mod id used for config namespace fallback
     * @param serverSupplier server supplier
     * @param slf4j SLF4J logger
     * @param configDir config directory override
     */
    public NeoForgePlatformProvider(String modId,
                                    Supplier<MinecraftServer> serverSupplier,
                                    Logger slf4j,
                                    Path configDir) {
        Logger effective = slf4j != null ? slf4j : LoggerFactory.getLogger("MagicUtils-NeoForge");
        this.serverSupplier = serverSupplier != null ? serverSupplier : ServerLifecycleHooks::getCurrentServer;
        this.logger = new NeoForgePlatformLogger(effective);
        this.consoleAudience = new NeoForgeConsoleAudience(this.serverSupplier, effective);
        Path resolvedConfig = configDir != null ? configDir : resolveConfigDir();
        this.configDir = resolvedConfig;
        this.useConfigNamespace = isDefaultConfigDir(resolvedConfig);
        this.configNamespace = (modId != null && !modId.trim().isEmpty()) ? modId.trim() : effective.getName();
        TaskScheduler scheduler = TaskSchedulers.create("MagicUtils-NeoForge", null);
        this.taskScheduler = scheduler;
        registerShutdownHookInternal(this.logger, scheduler::shutdown);
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public String resolveConfigNamespace(String pluginName) {
        if (!useConfigNamespace) {
            return null;
        }
        if (pluginName != null && !pluginName.trim().isEmpty()) {
            return pluginName;
        }
        return configNamespace;
    }

    @Override
    public String defaultConfigExtension() {
        return "jsonc";
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    @Override
    public Audience console() {
        return consoleAudience;
    }

    @Override
    public Collection<Audience> onlinePlayers() {
        MinecraftServer server = server();
        if (server == null) {
            return Collections.emptyList();
        }
        Collection<Audience> audiences = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            audiences.add(new NeoForgePlayerAudience(player));
        }
        return audiences;
    }

    @Override
    public void runOnMain(Runnable task) {
        if (task == null) {
            return;
        }
        MinecraftServer server = server();
        if (server == null || isServerThread(server)) {
            task.run();
            return;
        }
        server.execute(task);
    }

    @Override
    public boolean isMainThread() {
        MinecraftServer server = server();
        return server != null && isServerThread(server);
    }

    @Override
    public ThreadContext threadContext() {
        MinecraftServer server = server();
        if (server == null) {
            return ThreadContext.UNKNOWN;
        }
        return isServerThread(server) ? ThreadContext.MAIN : ThreadContext.WORKER;
    }

    @Override
    public TaskScheduler scheduler() {
        return taskScheduler;
    }

    @Override
    public void registerShutdownHook(Runnable hook) {
        registerShutdownHookInternal(logger, hook);
    }

    @Override
    public void unregisterShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        SHUTDOWN_HOOKS.remove(hook);
    }

    private MinecraftServer server() {
        return serverSupplier != null ? serverSupplier.get() : null;
    }

    private static void runShutdownHooks() {
        if (!SHUTDOWN_RAN.compareAndSet(false, true)) {
            return;
        }
        for (Runnable hook : SHUTDOWN_HOOKS) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                if (shutdownLogger != null) {
                    shutdownLogger.warn("Failed to run shutdown hook", e);
                }
            }
        }
        SHUTDOWN_HOOKS.clear();
    }

    private static void registerShutdownHookInternal(PlatformLogger logger, Runnable hook) {
        if (hook == null) {
            return;
        }
        SHUTDOWN_HOOKS.add(hook);
        if (shutdownLogger == null && logger != null) {
            shutdownLogger = logger;
        }
        if (SHUTDOWN_REGISTERED.compareAndSet(false, true)) {
            registerNeoForgeShutdownEvent();
            Runtime.getRuntime().addShutdownHook(new Thread(NeoForgePlatformProvider::runShutdownHooks,
                    "magicutils-neoforge-shutdown"));
        }
    }

    private static void registerNeoForgeShutdownEvent() {
        try {
            NeoForge.EVENT_BUS.addListener(NeoForgePlatformProvider::onServerStopping);
        } catch (Throwable ignored) {
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        runShutdownHooks();
    }

    private static Path resolveConfigDir() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (Throwable ignored) {
            return Path.of("config");
        }
    }

    private static boolean isDefaultConfigDir(Path configDir) {
        try {
            Path resolved = configDir != null ? configDir : Path.of("config");
            Path left = resolved.toAbsolutePath().normalize();
            Path right = resolveConfigDir().toAbsolutePath().normalize();
            return left.equals(right);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isServerThread(MinecraftServer server) {
        Boolean sameThread = invokeThreadCheck(server, "isSameThread");
        if (sameThread != null) {
            return sameThread;
        }
        Boolean onThread = invokeThreadCheck(server, "isOnThread");
        return onThread != null && onThread;
    }

    private static Boolean invokeThreadCheck(MinecraftServer server, String methodName) {
        if (server == null || methodName == null) {
            return null;
        }
        try {
            Method method = server.getClass().getMethod(methodName);
            Object result = method.invoke(server);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
