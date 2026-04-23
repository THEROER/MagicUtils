package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.Audience;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Wraps a Bukkit {@link CommandSender} as a platform-agnostic {@link Audience}.
 */
public class BukkitAudienceWrapper implements Audience {
    private final JavaPlugin plugin;
    @Getter
    private final CommandSender sender;

    /**
     * Wrap a {@link CommandSender} as an {@link Audience}.
     *
     * @param sender Bukkit command sender to wrap
     */
    public BukkitAudienceWrapper(CommandSender sender) {
        this(null, sender);
    }

    /**
     * Wrap a {@link CommandSender} as an {@link Audience} with a specific plugin context.
     *
     * @param plugin plugin instance for threading context (can be null for sync-only)
     * @param sender Bukkit command sender to wrap
     */
    public BukkitAudienceWrapper(JavaPlugin plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
    }

    @Override
    public void send(Component component) {
        if (sender == null || component == null) {
            return;
        }
        Runnable delivery = () -> sender.sendMessage(component);
        if (plugin == null) {
            delivery.run();
            return;
        }
        BukkitThreading.runForSender(plugin, sender, delivery);
    }

    @Override
    public UUID id() {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    @Override
    public String name() {
        return sender instanceof Player player ? player.getName() : null;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return sender != null && sender.hasPermission(permission);
    }
}
