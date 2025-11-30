package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Wraps a Bukkit {@link CommandSender} as a platform-agnostic {@link Audience}.
 */
public class BukkitAudienceWrapper implements Audience {
    private final CommandSender sender;

    /**
     * Wrap a {@link CommandSender} as an {@link Audience}.
     *
     * @param sender Bukkit command sender to wrap
     */
    public BukkitAudienceWrapper(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public void send(Component component) {
        sender.sendMessage(component);
    }

    @Override
    public UUID id() {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }
}
