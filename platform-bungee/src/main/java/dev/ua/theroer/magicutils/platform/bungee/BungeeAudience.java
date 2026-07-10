package dev.ua.theroer.magicutils.platform.bungee;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;

/**
 * Wraps a Bungee command sender as a MagicUtils audience.
 */
public final class BungeeAudience implements Audience {
    private final CommandSender source;
    private final UUID id;

    /**
     * Wraps a Bungee command sender (player or console) as an audience.
     *
     * @param source command sender to wrap
     */
    public BungeeAudience(CommandSender source) {
        this.source = source;
        this.id = source instanceof ProxiedPlayer player ? player.getUniqueId() : null;
    }

    @Override
    public void send(Component component) {
        if (source == null || component == null) {
            return;
        }
        source.sendMessage(BungeeComponentSerializer.toBaseComponents(component));
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return source instanceof ProxiedPlayer player ? player.getName() : null;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return source != null && source.hasPermission(permission);
    }
}
