package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Platform provider for Bukkit/Paper runtime.
 */
public class BukkitPlatformProvider implements Platform {
    private final JavaPlugin plugin;
    private final PlatformLogger logger;
    private final BukkitScheduler scheduler;

    public BukkitPlatformProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = new BukkitPlatformLogger(plugin.getLogger());
        this.scheduler = Bukkit.getScheduler();
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
        return wrap(Bukkit.getConsoleSender());
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

    private Audience wrap(CommandSender sender) {
        return new BukkitAudienceWrapper(sender);
    }
}
