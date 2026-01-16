package dev.ua.theroer.magicutils.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ProxyServer proxy;
    private final Object plugin;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final Path configDir;
    private final boolean cacheAudiences;
    private final AtomicBoolean shutdownListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean eventListenerRegistered = new AtomicBoolean(false);

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
        if (proxy != null && plugin != null) {
            proxy.getScheduler().buildTask(plugin, task).schedule();
            return;
        }
        task.run();
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
        if (proxy == null) {
            return new VelocityConsoleAudience(slf4j);
        }
        return new VelocityAudience(proxy.getConsoleCommandSource());
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
        runShutdownHooks();
    }

    /**
     * Cleans cached audiences for disconnected players.
     *
     * @param event disconnect event
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!cacheAudiences || event == null || event.getPlayer() == null) {
            return;
        }
        audienceCache.remove(event.getPlayer().getUniqueId());
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
