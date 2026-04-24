package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.ConfigNamespaceProvider;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycle;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleListener;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleType;
import dev.ua.theroer.magicutils.platform.PlayerLocale;
import dev.ua.theroer.magicutils.platform.PlayerLocaleListener;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ClientInformationUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final Collection<PlayerLifecycleListener> playerLifecycleListeners = new CopyOnWriteArrayList<>();
    private final Collection<PlayerLocaleListener> playerLocaleListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean eventListenersRegistered = new AtomicBoolean(false);

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
    public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        registerEventListeners();
        playerLifecycleListeners.add(listener);
        return () -> playerLifecycleListeners.remove(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        registerEventListeners();
        playerLocaleListeners.add(listener);
        publishCurrentPlayerLocales(listener);
        return () -> playerLocaleListeners.remove(listener);
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

    private void registerEventListeners() {
        if (!eventListenersRegistered.compareAndSet(false, true)) {
            return;
        }
        try {
            NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
            NeoForge.EVENT_BUS.addListener(this::onClientInformationUpdated);
        } catch (Throwable ignored) {
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        publishPlayerLifecycle(new PlayerLifecycle(player.getUUID(), player.getName().getString(),
                PlayerLifecycleType.JOIN));
        publishPlayerLocale(toPlayerLocale(player));
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        publishPlayerLifecycle(new PlayerLifecycle(player.getUUID(), player.getName().getString(),
                PlayerLifecycleType.LEAVE));
    }

    private void onClientInformationUpdated(ClientInformationUpdatedEvent event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        publishPlayerLocale(toPlayerLocale(event.getEntity(), event.getUpdatedInformation()));
    }

    private void publishCurrentPlayerLocales(PlayerLocaleListener listener) {
        if (listener == null) {
            return;
        }
        MinecraftServer server = server();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            publishPlayerLocale(listener, toPlayerLocale(player));
        }
    }

    private void publishPlayerLifecycle(PlayerLifecycle lifecycle) {
        if (lifecycle == null || !lifecycle.isValid() || playerLifecycleListeners.isEmpty()) {
            return;
        }
        for (PlayerLifecycleListener listener : playerLifecycleListeners) {
            try {
                listener.onPlayerLifecycle(lifecycle);
            } catch (RuntimeException e) {
                logger.warn("Failed to deliver NeoForge player lifecycle listener", e);
            }
        }
    }

    private void publishPlayerLocale(PlayerLocale playerLocale) {
        if (playerLocale == null || !playerLocale.isValid() || playerLocaleListeners.isEmpty()) {
            return;
        }
        for (PlayerLocaleListener listener : playerLocaleListeners) {
            publishPlayerLocale(listener, playerLocale);
        }
    }

    private void publishPlayerLocale(PlayerLocaleListener listener, PlayerLocale playerLocale) {
        if (listener == null || playerLocale == null || !playerLocale.isValid()) {
            return;
        }
        try {
            listener.onPlayerLocale(playerLocale);
        } catch (RuntimeException e) {
            logger.warn("Failed to deliver NeoForge player locale listener", e);
        }
    }

    private PlayerLocale toPlayerLocale(ServerPlayer player) {
        return toPlayerLocale(player, null);
    }

    private PlayerLocale toPlayerLocale(ServerPlayer player, Object clientInformation) {
        if (player == null) {
            return null;
        }
        String localeTag = extractLocaleTag(player, clientInformation);
        if (localeTag == null || localeTag.isBlank()) {
            return null;
        }
        return new PlayerLocale(player.getUUID(), player.getName().getString(), localeTag);
    }

    private String extractLocaleTag(ServerPlayer player, Object clientInformation) {
        String fromInfo = extractLanguageTag(clientInformation);
        if (fromInfo != null && !fromInfo.isBlank()) {
            return fromInfo;
        }
        if (player == null) {
            return null;
        }
        Object info = invoke(player, "getClientInformation");
        if (info == null) {
            info = invoke(player, "clientInformation");
        }
        if (info == null) {
            info = invoke(player, "getClientOptions");
        }
        return extractLanguageTag(info);
    }

    private static String extractLanguageTag(Object info) {
        if (info == null) {
            return null;
        }
        Object language = invoke(info, "language");
        if (language == null) {
            language = invoke(info, "languageCode");
        }
        if (language == null) {
            language = invoke(info, "getLanguage");
        }
        return language instanceof String value && !value.isBlank() ? value : null;
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

    private static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
