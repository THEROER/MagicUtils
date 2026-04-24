package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleListener;
import dev.ua.theroer.magicutils.platform.PlayerLocaleListener;
import dev.ua.theroer.magicutils.platform.PlayerMessageListener;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Platform provider for Folia (region-based Bukkit).
 */
public final class FoliaPlatformProvider implements Platform, ShutdownHookRegistrar {
    private final JavaPlugin plugin;
    private final BukkitPlatformProvider delegate;

    /**
     * Creates a new Folia platform provider.
     *
     * @param plugin the plugin instance
     */
    public FoliaPlatformProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.delegate = new BukkitPlatformProvider(plugin);
    }

    @Override
    public Path configDir() {
        return delegate.configDir();
    }

    @Override
    public PlatformLogger logger() {
        return delegate.logger();
    }

    @Override
    public Audience console() {
        return delegate.console();
    }

    @Override
    public Collection<Audience> onlinePlayers() {
        return delegate.onlinePlayers();
    }

    @Override
    public void runOnMain(Runnable task) {
        BukkitThreading.runGlobal(plugin, task);
    }

    @Override
    public void runForAudience(Audience audience, Runnable task) {
        delegate.runForAudience(audience, task);
    }

    @Override
    public boolean isMainThread() {
        return BukkitThreading.isGlobalThread();
    }

    @Override
    public TaskScheduler scheduler() {
        return delegate.scheduler();
    }

    @Override
    public void registerShutdownHook(Runnable hook) {
        delegate.registerShutdownHook(hook);
    }

    @Override
    public void unregisterShutdownHook(Runnable hook) {
        delegate.unregisterShutdownHook(hook);
    }

    @Override
    public ListenerSubscription subscribePlayerMessages(PlayerMessageListener listener) {
        return delegate.subscribePlayerMessages(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
        return delegate.subscribePlayerLifecycle(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
        return delegate.subscribePlayerLocales(listener);
    }
}
