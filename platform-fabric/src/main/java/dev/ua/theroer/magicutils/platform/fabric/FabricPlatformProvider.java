package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.AudienceResolver;
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
import dev.ua.theroer.magicutils.platform.PlayerMessage;
import dev.ua.theroer.magicutils.platform.PlayerMessageListener;
import dev.ua.theroer.magicutils.platform.PlayerMessageType;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/**
 * Platform provider for Fabric runtime.
 */
@SuppressWarnings("doclint:missing")
public final class FabricPlatformProvider implements Platform, ConfigNamespaceProvider, ConfigFormatProvider, ShutdownHookRegistrar {
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
    private final List<PlayerLifecycleListener> playerLifecycleListeners = new CopyOnWriteArrayList<>();
    private final List<PlayerLocaleListener> playerLocaleListeners = new CopyOnWriteArrayList<>();
    private final List<PlayerMessageListener> playerMessageListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean playerLifecycleHooksRegistered = new AtomicBoolean(false);
    private final AtomicBoolean playerMessageHooksRegistered = new AtomicBoolean(false);
    private final AtomicBoolean inlineMainFallbackWarned = new AtomicBoolean(false);

    public FabricPlatformProvider(MinecraftServer server) {
        this(() -> server, LoggerFactory.getLogger("MagicUtils-Fabric"));
    }

    public FabricPlatformProvider(Supplier<MinecraftServer> serverSupplier) {
        this(serverSupplier, LoggerFactory.getLogger("MagicUtils-Fabric"));
    }

    public FabricPlatformProvider(MinecraftServer server, Logger slf4j) {
        this(() -> server, slf4j, resolveConfigDir());
    }

    public FabricPlatformProvider(Supplier<MinecraftServer> serverSupplier, Logger slf4j) {
        this(serverSupplier, slf4j, resolveConfigDir());
    }

    public FabricPlatformProvider(Supplier<MinecraftServer> serverSupplier, Logger slf4j, Path configDir) {
        Logger effective = slf4j != null ? slf4j : LoggerFactory.getLogger("MagicUtils-Fabric");
        this.serverSupplier = serverSupplier != null ? serverSupplier : () -> null;
        this.logger = new FabricPlatformLogger(effective);
        this.consoleAudience = new FabricPlainConsoleAudience(this.logger);
        Path resolvedConfig = configDir != null ? configDir : Path.of("config");
        this.configDir = resolvedConfig;
        this.useConfigNamespace = isDefaultConfigDir(resolvedConfig);
        this.configNamespace = effective.getName();
        this.taskScheduler = TaskSchedulers.create("MagicUtils-Fabric", this);
        AudienceResolver.registerFactory(obj -> obj instanceof ServerPlayer sp ? wrap(sp) : null);
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
        return server.getPlayerList().getPlayers().stream()
                .map(this::wrap)
                .collect(Collectors.toList());
    }

    @Override
    public void runOnMain(Runnable task) {
        if (task == null) {
            return;
        }
        MinecraftServer server = server();
        if (server == null) {
            warnInlineMainFallback();
            task.run();
            return;
        }
        if (server.isSameThread()) {
            task.run();
            return;
        }
        server.execute(task);
    }

    @Override
    public boolean isMainThread() {
        MinecraftServer server = server();
        return server != null && server.isSameThread();
    }

    @Override
    public ThreadContext threadContext() {
        MinecraftServer server = server();
        if (server == null) {
            return ThreadContext.UNKNOWN;
        }
        return server.isSameThread() ? ThreadContext.MAIN : ThreadContext.WORKER;
    }

    @Override
    public TaskScheduler scheduler() {
        return taskScheduler;
    }

    @Override
    public ListenerSubscription subscribePlayerMessages(PlayerMessageListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        playerMessageListeners.add(listener);
        registerPlayerMessageHooks();
        return () -> playerMessageListeners.remove(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        playerLifecycleListeners.add(listener);
        registerPlayerLifecycleHooks();
        return () -> playerLifecycleListeners.remove(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        playerLocaleListeners.add(listener);
        registerPlayerLifecycleHooks();
        publishCurrentPlayerLocales(listener);
        return () -> playerLocaleListeners.remove(listener);
    }

    @Override
    public void registerShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        SHUTDOWN_HOOKS.add(hook);
        if (shutdownLogger == null) {
            shutdownLogger = logger;
        }
        if (SHUTDOWN_REGISTERED.compareAndSet(false, true)) {
            registerFabricShutdownEvent();
            Runtime.getRuntime().addShutdownHook(new Thread(FabricPlatformProvider::runShutdownHooks,
                    "magicutils-config-shutdown"));
        }
    }

    @Override
    public void unregisterShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        SHUTDOWN_HOOKS.remove(hook);
    }

    private Audience wrap(ServerPlayer player) {
        return new FabricAudience(player);
    }

    private MinecraftServer server() {
        return serverSupplier != null ? serverSupplier.get() : null;
    }

    private void warnInlineMainFallback() {
        if (inlineMainFallbackWarned.compareAndSet(false, true)) {
            logger.warn("Fabric server is not available yet; running task inline because the main-thread executor is unavailable.");
        }
    }

    private void registerPlayerMessageHooks() {
        if (!playerMessageHooksRegistered.compareAndSet(false, true)) {
            return;
        }
        registerFabricMessageEvent(
                "CHAT_MESSAGE",
                "net.fabricmc.fabric.api.message.v1.ServerMessageEvents$ChatMessage",
                (proxy, method, args) -> {
                    if (args == null || args.length < 2) {
                        return null;
                    }
                    String content = extractMessageContent(args[0]);
                    final ServerPlayer[] senderHolder = new ServerPlayer[1];
                    ReflectiveAccess.cast(args[1], ServerPlayer.class)
                            .ifPresent(value -> senderHolder[0] = value);
                    ServerPlayer sender = senderHolder[0];
                    publishPlayerMessage(sender, content, PlayerMessageType.CHAT);
                    return null;
                }
        );
        registerFabricMessageEvent(
                "COMMAND_MESSAGE",
                "net.fabricmc.fabric.api.message.v1.ServerMessageEvents$CommandMessage",
                (proxy, method, args) -> {
                    if (args == null || args.length < 2) {
                        return null;
                    }
                    String content = extractMessageContent(args[0]);
                    Object source = args[1];
                    final ServerPlayer[] senderHolder = new ServerPlayer[1];
                    ReflectiveAccess.publicMethod(source.getClass(), "getPlayer")
                            .flatMap(getPlayer -> ReflectiveAccess.invoke(getPlayer, source))
                            .flatMap(value -> ReflectiveAccess.cast(value, ServerPlayer.class))
                            .ifPresent(value -> senderHolder[0] = value);
                    ServerPlayer sender = senderHolder[0];
                    publishPlayerMessage(sender, content, PlayerMessageType.COMMAND);
                    return null;
                }
        );
    }

    private void registerPlayerLifecycleHooks() {
        if (!playerLifecycleHooksRegistered.compareAndSet(false, true)) {
            return;
        }
        registerFabricConnectionEvent(
                "JOIN",
                "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Join",
                (proxy, method, args) -> {
                    if (args == null || args.length < 1) {
                        return null;
                    }
                    ServerPlayer player = extractPlayer(args[0]);
                    publishPlayerLifecycle(player, PlayerLifecycleType.JOIN);
                    publishPlayerLocale(player);
                    return null;
                }
        );
        registerFabricConnectionEvent(
                "DISCONNECT",
                "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Disconnect",
                (proxy, method, args) -> {
                    if (args == null || args.length < 1) {
                        return null;
                    }
                    publishPlayerLifecycle(extractPlayer(args[0]), PlayerLifecycleType.LEAVE);
                    return null;
                }
        );
    }

    private void registerFabricMessageEvent(
            String eventFieldName,
            String listenerClassName,
            InvocationHandler handler
    ) {
        Class<?> eventsClass = ReflectiveAccess.loadClass(
                "net.fabricmc.fabric.api.message.v1.ServerMessageEvents"
        ).orElse(null);
        Class<?> listenerType = ReflectiveAccess.loadClass(listenerClassName).orElse(null);
        if (eventsClass == null || listenerType == null) {
            return;
        }

        Object event = ReflectiveAccess.publicField(eventsClass, eventFieldName)
                .flatMap(field -> ReflectiveAccess.readField(field, null))
                .orElse(null);
        if (event == null) {
            return;
        }

        Method register = ReflectiveAccess.publicMethod(event.getClass(), "register", listenerType)
                .orElse(null);
        if (register == null) {
            return;
        }

        Object listener = Proxy.newProxyInstance(
                eventsClass.getClassLoader(),
                new Class<?>[]{listenerType},
                handler
        );
        ReflectiveAccess.invoke(register, event, listener);
    }

    private void registerFabricConnectionEvent(
            String eventFieldName,
            String listenerClassName,
            InvocationHandler handler
    ) {
        Class<?> eventsClass = ReflectiveAccess.loadClass(
                "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents"
        ).orElse(null);
        Class<?> listenerType = ReflectiveAccess.loadClass(listenerClassName).orElse(null);
        if (eventsClass == null || listenerType == null) {
            return;
        }

        Object event = ReflectiveAccess.publicField(eventsClass, eventFieldName)
                .flatMap(field -> ReflectiveAccess.readField(field, null))
                .orElse(null);
        if (event == null) {
            return;
        }

        Method register = ReflectiveAccess.publicMethod(event.getClass(), "register", listenerType)
                .orElse(null);
        if (register == null) {
            return;
        }

        Object listener = Proxy.newProxyInstance(
                eventsClass.getClassLoader(),
                new Class<?>[]{listenerType},
                handler
        );
        ReflectiveAccess.invoke(register, event, listener);
    }

    private void publishPlayerMessage(ServerPlayer player, String message, PlayerMessageType type) {
        if (player == null || message == null || message.isBlank() || type == null) {
            return;
        }
        PlayerMessage playerMessage = new PlayerMessage(
                player.getUUID(),
                player.getName().getString(),
                message,
                type
        );
        if (!playerMessage.isValid()) {
            return;
        }
        for (PlayerMessageListener listener : playerMessageListeners) {
            try {
                listener.onPlayerMessage(playerMessage);
            } catch (RuntimeException e) {
                logger.warn("Failed to dispatch Fabric player message", e);
            }
        }
    }

    private void publishPlayerLifecycle(ServerPlayer player, PlayerLifecycleType type) {
        if (player == null || type == null || playerLifecycleListeners.isEmpty()) {
            return;
        }
        PlayerLifecycle lifecycle = new PlayerLifecycle(
                player.getUUID(),
                player.getName().getString(),
                type
        );
        if (!lifecycle.isValid()) {
            return;
        }
        for (PlayerLifecycleListener listener : playerLifecycleListeners) {
            try {
                listener.onPlayerLifecycle(lifecycle);
            } catch (RuntimeException e) {
                logger.warn("Failed to dispatch Fabric player lifecycle", e);
            }
        }
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

    private void publishPlayerLocale(ServerPlayer player) {
        publishPlayerLocale(toPlayerLocale(player));
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
            logger.warn("Failed to dispatch Fabric player locale", e);
        }
    }

    private PlayerLocale toPlayerLocale(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        String localeTag = extractLocaleTag(player);
        if (localeTag == null || localeTag.isBlank()) {
            return null;
        }
        return new PlayerLocale(player.getUUID(), player.getName().getString(), localeTag);
    }

    private static String extractLocaleTag(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        Object options = ReflectiveAccess.publicMethod(player.getClass(), "getClientOptions")
                .flatMap(method -> ReflectiveAccess.invoke(method, player))
                .orElse(null);
        if (options == null) {
            options = ReflectiveAccess.publicMethod(player.getClass(), "getClientInformation")
                    .flatMap(method -> ReflectiveAccess.invoke(method, player))
                    .orElse(null);
        }
        if (options == null) {
            options = ReflectiveAccess.publicMethod(player.getClass(), "clientInformation")
                    .flatMap(method -> ReflectiveAccess.invoke(method, player))
                    .orElse(null);
        }
        return extractLanguageTag(options);
    }

    private static String extractLanguageTag(Object options) {
        if (options == null) {
            return null;
        }
        Object language = ReflectiveAccess.publicMethod(options.getClass(), "language")
                .flatMap(method -> ReflectiveAccess.invoke(method, options))
                .orElse(null);
        if (language == null) {
            language = ReflectiveAccess.publicMethod(options.getClass(), "languageCode")
                    .flatMap(method -> ReflectiveAccess.invoke(method, options))
                    .orElse(null);
        }
        if (language == null) {
            language = ReflectiveAccess.publicMethod(options.getClass(), "getLanguage")
                    .flatMap(method -> ReflectiveAccess.invoke(method, options))
                    .orElse(null);
        }
        return language instanceof String value && !value.isBlank() ? value : null;
    }

    private static ServerPlayer extractPlayer(Object handler) {
        if (handler == null) {
            return null;
        }
        final ServerPlayer[] playerHolder = new ServerPlayer[1];
        ReflectiveAccess.publicMethod(handler.getClass(), "getPlayer")
                .flatMap(method -> ReflectiveAccess.invoke(method, handler))
                .flatMap(value -> ReflectiveAccess.cast(value, ServerPlayer.class))
                .ifPresent(value -> playerHolder[0] = value);
        if (playerHolder[0] != null) {
            return playerHolder[0];
        }
        final ServerPlayer[] fieldHolder = new ServerPlayer[1];
        ReflectiveAccess.publicField(handler.getClass(), "player")
                .flatMap(field -> ReflectiveAccess.readField(field, handler))
                .flatMap(value -> ReflectiveAccess.cast(value, ServerPlayer.class))
                .ifPresent(value -> fieldHolder[0] = value);
        return fieldHolder[0];
    }

    private static String extractMessageContent(Object signedMessage) {
        if (signedMessage == null) {
            return "";
        }
        Object content = ReflectiveAccess.publicMethod(signedMessage.getClass(), "getContent")
                .flatMap(method -> ReflectiveAccess.invoke(method, signedMessage))
                .orElse(null);
        if (content == null) {
            return "";
        }
        return ReflectiveAccess.publicMethod(content.getClass(), "getString")
                .flatMap(method -> ReflectiveAccess.invoke(method, content))
                .map(Object::toString)
                .orElse("");
    }

    private static Path resolveConfigDir() {
        try {
            return FabricLoader.getInstance().getConfigDir();
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

    private static void registerFabricShutdownEvent() {
        Class<?> eventsClass = ReflectiveAccess.loadClass(
                "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents"
        ).orElse(null);
        if (eventsClass == null) {
            return;
        }

        Class<?> listenerType = ReflectiveAccess.loadClass(
                "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopping"
        ).orElse(null);
        if (listenerType == null) {
            return;
        }

        Object stoppingEvent = ReflectiveAccess.publicField(eventsClass, "SERVER_STOPPING")
                .flatMap(field -> ReflectiveAccess.readField(field, null))
                .orElse(null);
        if (stoppingEvent == null) {
            return;
        }

        Method register = ReflectiveAccess.publicMethod(stoppingEvent.getClass(), "register", listenerType)
                .orElse(null);
        if (register == null) {
            return;
        }

        Object listener = Proxy.newProxyInstance(eventsClass.getClassLoader(), new Class<?>[]{listenerType},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        runShutdownHooks();
                        return null;
                    }
                });
        ReflectiveAccess.invoke(register, stoppingEvent, listener);
    }
}
