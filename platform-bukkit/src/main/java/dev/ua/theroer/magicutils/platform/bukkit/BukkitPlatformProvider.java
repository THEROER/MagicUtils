package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.config.adapters.AdaptersBootstrap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Platform provider for Bukkit/Paper runtime.
 */
public class BukkitPlatformProvider implements Platform, ShutdownHookRegistrar {
    private final JavaPlugin plugin;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final BukkitScheduler scheduler;
    private final List<Runnable> shutdownHooks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean shutdownListenerRegistered = new AtomicBoolean(false);

    /**
     * Create a Bukkit/Paper platform adapter around the given plugin.
     *
     * @param plugin owning plugin instance
     */
    public BukkitPlatformProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = new BukkitPlatformLogger(plugin.getLogger());
        this.scheduler = Bukkit.getScheduler();
        this.consoleAudience = new BukkitConsoleAudience(plugin.getLogger(), plugin.getName());
        AdaptersBootstrap.registerDefaults();
    }

    @Override
    public Path configDir() {
        return plugin.getDataFolder().toPath();
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
        return Bukkit.getOnlinePlayers().stream()
                .map(this::wrap)
                .collect(Collectors.toList());
    }

    @Override
    public void runOnMain(Runnable task) {
        if (isMainThread()) {
            task.run();
            return;
        }
        scheduler.runTask(plugin, task);
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void registerShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        shutdownHooks.add(hook);
        if (shutdownListenerRegistered.compareAndSet(false, true)) {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPluginDisable(PluginDisableEvent event) {
                    if (event.getPlugin() != plugin) {
                        return;
                    }
                    for (Runnable runnable : shutdownHooks) {
                        try {
                            runnable.run();
                        } catch (RuntimeException e) {
                            logger.warn("Failed to run shutdown hook", e);
                        }
                    }
                    shutdownHooks.clear();
                }
            }, plugin);
        }
    }

    @Override
    public void unregisterShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        shutdownHooks.remove(hook);
    }

    private Audience wrap(CommandSender sender) {
        return new BukkitAudienceWrapper(sender);
    }
}
