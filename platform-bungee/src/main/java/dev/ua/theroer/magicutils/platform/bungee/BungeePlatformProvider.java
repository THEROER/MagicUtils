package dev.ua.theroer.magicutils.platform.bungee;

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
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Platform provider for BungeeCord runtime.
 */
public final class BungeePlatformProvider implements Platform, ShutdownHookRegistrar, Listener {
    private static final Path DEFAULT_CONFIG_DIR = Path.of("plugins", "magicutils");
    private static final Set<Runnable> SHUTDOWN_HOOKS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean JVM_SHUTDOWN_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN_RAN = new AtomicBoolean(false);
    private static volatile PlatformLogger shutdownLogger;

    private final Map<UUID, Audience> audienceCache = new ConcurrentHashMap<>();
    private final Collection<PlayerLifecycleListener> playerLifecycleListeners = new CopyOnWriteArrayList<>();
    private final Collection<PlayerMessageListener> playerMessageListeners = new CopyOnWriteArrayList<>();
    private final ProxyServer proxy;
    private final Plugin plugin;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final Path configDir;
    private final boolean cacheAudiences;
    private final AtomicBoolean eventListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean inlineMainFallbackWarned = new AtomicBoolean(false);
    private final TaskScheduler taskScheduler;

    /**
     * Creates a Bungee platform provider with explicit logger and data directory.
     *
     * @param proxy Bungee proxy
     * @param jul backing JUL logger
     * @param dataDirectory config/data directory
     * @param plugin owning plugin
     */
    public BungeePlatformProvider(ProxyServer proxy, Logger jul, Path dataDirectory, Plugin plugin) {
        this.proxy = proxy;
        Logger effective = jul != null ? jul : proxy != null ? proxy.getLogger() : Logger.getLogger("MagicUtils-Bungee");
        this.logger = new BungeePlatformLogger(effective);
        this.consoleAudience = new BungeeConsoleAudience(effective, effective.getName());
        this.configDir = dataDirectory != null ? dataDirectory : DEFAULT_CONFIG_DIR;
        this.plugin = plugin;
        this.cacheAudiences = plugin != null;
        this.taskScheduler = TaskSchedulers.create("MagicUtils-Bungee", this);
        if (cacheAudiences) {
            registerEventListener();
        }
    }

    /**
     * Creates a Bungee platform provider with explicit logger and data directory.
     *
     * @param proxy Bungee proxy
     * @param jul backing JUL logger
     * @param dataDirectory config/data directory
     */
    public BungeePlatformProvider(ProxyServer proxy, Logger jul, Path dataDirectory) {
        this(proxy, jul, dataDirectory, null);
    }

    /**
     * Creates a Bungee platform provider with default logger.
     *
     * @param proxy Bungee proxy
     * @param dataDirectory config/data directory
     */
    public BungeePlatformProvider(ProxyServer proxy, Path dataDirectory) {
        this(proxy, proxy != null ? proxy.getLogger() : null, dataDirectory, null);
    }

    /**
     * Creates a Bungee platform provider with default logger and config directory.
     *
     * @param proxy Bungee proxy
     */
    public BungeePlatformProvider(ProxyServer proxy) {
        this(proxy, proxy != null ? proxy.getLogger() : null, DEFAULT_CONFIG_DIR, null);
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
        return proxy.getPlayers().stream()
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
        proxy.getScheduler().schedule(plugin, task, 0L, TimeUnit.MILLISECONDS);
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
        if (JVM_SHUTDOWN_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(BungeePlatformProvider::runShutdownHooks,
                    "magicutils-bungee-shutdown"));
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        publishPlayerLifecycle(new PlayerLifecycle(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                PlayerLifecycleType.JOIN
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDisconnect(PlayerDisconnectEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (cacheAudiences) {
            audienceCache.remove(event.getPlayer().getUniqueId());
        }
        publishPlayerLifecycle(new PlayerLifecycle(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                PlayerLifecycleType.LEAVE
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (event == null || event.isCancelled() || playerMessageListeners.isEmpty()) {
            return;
        }
        if (!(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        publishPlayerMessage(new PlayerMessage(
                player.getUniqueId(),
                player.getName(),
                message,
                event.isCommand() ? PlayerMessageType.COMMAND : PlayerMessageType.CHAT
        ));
    }

    private Audience wrap(ProxiedPlayer player) {
        if (player == null) {
            return consoleAudience;
        }
        if (!cacheAudiences) {
            return new BungeeAudience(player);
        }
        return audienceCache.computeIfAbsent(player.getUniqueId(), id -> new BungeeAudience(player));
    }

    private void warnInlineMainFallback() {
        if (inlineMainFallbackWarned.compareAndSet(false, true)) {
            logger.warn("Bungee plugin context is unavailable; running task inline because no scheduler owner is accessible.");
        }
    }

    private void registerEventListener() {
        if (proxy == null || plugin == null) {
            return;
        }
        if (eventListenerRegistered.compareAndSet(false, true)) {
            proxy.getPluginManager().registerListener(plugin, this);
        }
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
