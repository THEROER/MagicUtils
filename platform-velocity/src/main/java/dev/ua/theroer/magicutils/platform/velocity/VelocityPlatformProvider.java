package dev.ua.theroer.magicutils.platform.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Platform provider for Velocity runtime.
 */
public final class VelocityPlatformProvider implements Platform, ShutdownHookRegistrar {
    private static final Path DEFAULT_CONFIG_DIR = Path.of("plugins", "magicutils");

    private final ProxyServer proxy;
    private final Object plugin;
    private final PlatformLogger logger;
    private final Logger slf4j;
    private final Audience consoleAudience;
    private final Path configDir;
    private final List<Runnable> shutdownHooks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean shutdownRegistered = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRan = new AtomicBoolean(false);

    public VelocityPlatformProvider(ProxyServer proxy, Logger slf4j, Path dataDirectory, Object plugin) {
        this.proxy = proxy;
        Logger effective = slf4j != null ? slf4j : LoggerFactory.getLogger("MagicUtils-Velocity");
        this.slf4j = effective;
        this.logger = new VelocityPlatformLogger(effective);
        this.consoleAudience = resolveConsoleAudience(proxy, effective);
        this.configDir = dataDirectory != null ? dataDirectory : DEFAULT_CONFIG_DIR;
        this.plugin = plugin;
    }

    public VelocityPlatformProvider(ProxyServer proxy, Logger slf4j, Path dataDirectory) {
        this(proxy, slf4j, dataDirectory, null);
    }

    public VelocityPlatformProvider(ProxyServer proxy, Path dataDirectory) {
        this(proxy, LoggerFactory.getLogger("MagicUtils-Velocity"), dataDirectory, null);
    }

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
        task.run();
    }

    @Override
    public boolean isMainThread() {
        return true;
    }

    @Override
    public void registerShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        shutdownHooks.add(hook);
        if (shutdownRegistered.compareAndSet(false, true)) {
            registerShutdownListener();
            Runtime.getRuntime().addShutdownHook(new Thread(this::runShutdownHooks, "magicutils-velocity-shutdown"));
        }
    }

    private Audience wrap(Player player) {
        return new VelocityAudience(player);
    }

    private Audience resolveConsoleAudience(ProxyServer proxy, Logger slf4j) {
        if (proxy == null) {
            return new VelocityConsoleAudience(slf4j);
        }
        return new VelocityAudience(proxy.getConsoleCommandSource());
    }

    private void registerShutdownListener() {
        if (proxy == null || plugin == null) {
            return;
        }
        proxy.getEventManager().register(plugin, this);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        runShutdownHooks();
    }

    private void runShutdownHooks() {
        if (!shutdownRan.compareAndSet(false, true)) {
            return;
        }
        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                logger.warn("Failed to run shutdown hook", e);
            }
        }
        shutdownHooks.clear();
    }
}
