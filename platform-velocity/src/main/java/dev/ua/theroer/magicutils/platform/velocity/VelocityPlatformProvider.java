package dev.ua.theroer.magicutils.platform.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycle;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleListener;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleType;
import dev.ua.theroer.magicutils.platform.PlayerMessage;
import dev.ua.theroer.magicutils.platform.PlayerMessageListener;
import dev.ua.theroer.magicutils.platform.PlayerMessageType;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Platform provider for Velocity runtime.
 */
public final class VelocityPlatformProvider implements Platform, ShutdownHookRegistrar {
    private static final Path DEFAULT_CONFIG_DIR = Path.of("plugins", "magicutils");
    private static final Set<Runnable> SHUTDOWN_HOOKS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean JVM_SHUTDOWN_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN_RAN = new AtomicBoolean(false);
    private static volatile PlatformLogger shutdownLogger;

    private final Map<UUID, Audience> audienceCache = new ConcurrentHashMap<>();
    private final Collection<PlayerLifecycleListener> playerLifecycleListeners = new CopyOnWriteArrayList<>();
    private final Collection<PlayerMessageListener> playerMessageListeners = new CopyOnWriteArrayList<>();
    private final ProxyServer proxy;
    private final Object plugin;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final Path configDir;
    private final boolean cacheAudiences;
    private final AtomicBoolean shutdownListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean eventListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean inlineMainFallbackWarned = new AtomicBoolean(false);
    private final TaskScheduler taskScheduler;

    /**
     * Creates a Velocity platform provider with explicit logger and data directory.
     *
     * @param proxy Velocity proxy server
     * @param slf4j backing SLF4J logger
     * @param dataDirectory config/data directory
     * @param plugin plugin instance used for event registration
     */
    public VelocityPlatformProvider(ProxyServer proxy, Logger slf4j, Path dataDirectory, Object plugin) {
        this.proxy = proxy;
        Logger effective = slf4j != null ? slf4j : LoggerFactory.getLogger("MagicUtils-Velocity");
        this.logger = new VelocityPlatformLogger(effective);
        this.consoleAudience = resolveConsoleAudience(proxy, effective);
        this.configDir = dataDirectory != null ? dataDirectory : DEFAULT_CONFIG_DIR;
        this.plugin = plugin;
        this.cacheAudiences = plugin != null;
        this.taskScheduler = TaskSchedulers.create("MagicUtils-Velocity", this);
        if (cacheAudiences) {
            registerEventListener();
        }
    }

    /**
     * Creates a Velocity platform provider with explicit logger and data directory.
     *
     * @param proxy Velocity proxy server
     * @param slf4j backing SLF4J logger
     * @param dataDirectory config/data directory
     */
    public VelocityPlatformProvider(ProxyServer proxy, Logger slf4j, Path dataDirectory) {
        this(proxy, slf4j, dataDirectory, null);
    }

    /**
     * Creates a Velocity platform provider with default logger.
     *
     * @param proxy Velocity proxy server
     * @param dataDirectory config/data directory
     */
    public VelocityPlatformProvider(ProxyServer proxy, Path dataDirectory) {
        this(proxy, LoggerFactory.getLogger("MagicUtils-Velocity"), dataDirectory, null);
    }

    /**
     * Creates a Velocity platform provider with default logger and config directory.
     *
     * @param proxy Velocity proxy server
     */
    public VelocityPlatformProvider(ProxyServer proxy) {
        this(proxy, LoggerFactory.getLogger("MagicUtils-Velocity"), DEFAULT_CONFIG_DIR, null);
    }

    @Override
    public Path configDir() {
        return configDir;
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
        if (proxy == null) {
            return Collections.emptyList();
        }
        return proxy.getAllPlayers().stream()
                .map(this::wrap)
                .collect(Collectors.toList());
    }

    @Override
    public void runOnMain(Runnable task) {
        if (task == null) {
            return;
        }
        if (proxy == null || plugin == null) {
            warnInlineMainFallback();
            task.run();
            return;
        }
        proxy.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public boolean isMainThread() {
        return false;
    }

    @Override
    public ThreadContext threadContext() {
        return ThreadContext.UNKNOWN;
    }

    @Override
    public TaskScheduler scheduler() {
        return taskScheduler;
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
        if (shutdownListenerRegistered.compareAndSet(false, true)) {
            registerEventListener();
        }
        if (JVM_SHUTDOWN_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(VelocityPlatformProvider::runShutdownHooks,
                    "magicutils-velocity-shutdown"));
        }
    }

    @Override
    public void unregisterShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        SHUTDOWN_HOOKS.remove(hook);
    }

    @Override
    public ListenerSubscription subscribePlayerMessages(PlayerMessageListener listener) {
        if (listener == null || proxy == null || plugin == null) {
            return ListenerSubscription.noop();
        }
        registerEventListener();
        playerMessageListeners.add(listener);
        return () -> playerMessageListeners.remove(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
        if (listener == null || proxy == null || plugin == null) {
            return ListenerSubscription.noop();
        }
        registerEventListener();
        playerLifecycleListeners.add(listener);
        return () -> playerLifecycleListeners.remove(listener);
    }

    private Audience wrap(Player player) {
        if (player == null) {
            return consoleAudience;
        }
        if (!cacheAudiences) {
            return new VelocityAudience(player);
        }
        return audienceCache.computeIfAbsent(player.getUniqueId(), id -> new VelocityAudience(player));
    }

    private Audience resolveConsoleAudience(ProxyServer proxy, Logger slf4j) {
        return new VelocityConsoleAudience(slf4j);
    }

    private void warnInlineMainFallback() {
        if (inlineMainFallbackWarned.compareAndSet(false, true)) {
            logger.warn("Velocity plugin context is unavailable; running task inline because no main-thread scheduler is accessible.");
        }
    }

    private void registerEventListener() {
        if (proxy == null || plugin == null) {
            return;
        }
        if (eventListenerRegistered.compareAndSet(false, true)) {
            proxy.getEventManager().register(plugin, this);
        }
    }

    /**
     * Handles Velocity shutdown event and runs registered hooks.
     *
     * @param event shutdown event
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        audienceCache.clear();
        playerLifecycleListeners.clear();
        playerMessageListeners.clear();
        runShutdownHooks();
    }

    /**
     * Cleans cached audiences for disconnected players.
     *
     * @param event disconnect event
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (cacheAudiences) {
            audienceCache.remove(event.getPlayer().getUniqueId());
        }
        publishPlayerLifecycle(new PlayerLifecycle(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                PlayerLifecycleType.LEAVE
        ));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        publishPlayerLifecycle(new PlayerLifecycle(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                PlayerLifecycleType.JOIN
        ));
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        if (event == null || event.getPlayer() == null || playerMessageListeners.isEmpty()) {
            return;
        }
        publishPlayerMessage(new PlayerMessage(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                event.getMessage(),
                PlayerMessageType.CHAT
        ));
    }

    @Subscribe(order = PostOrder.LAST)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (event == null || playerMessageListeners.isEmpty() || !event.getResult().isAllowed()) {
            return;
        }
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }
        String command = event.getCommand();
        if (command == null || command.isBlank()) {
            return;
        }
        publishPlayerMessage(new PlayerMessage(
                player.getUniqueId(),
                player.getUsername(),
                command,
                PlayerMessageType.COMMAND
        ));
    }

    private void publishPlayerMessage(PlayerMessage message) {
        if (message == null || !message.isValid() || playerMessageListeners.isEmpty()) {
            return;
        }
        for (PlayerMessageListener listener : playerMessageListeners) {
            try {
                listener.onPlayerMessage(message);
            } catch (RuntimeException e) {
                logger.warn("Failed to deliver player message listener", e);
            }
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
                logger.warn("Failed to deliver player lifecycle listener", e);
            }
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
}
