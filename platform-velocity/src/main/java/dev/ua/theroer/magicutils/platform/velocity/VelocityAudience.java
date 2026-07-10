package dev.ua.theroer.magicutils.platform.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Wraps a Velocity command source as a MagicUtils audience.
 */
public final class VelocityAudience implements Audience {
    private final CommandSource source;
    private final UUID id;

    /**
     * Wraps a Velocity command source (player or console) as an audience.
     *
     * @param source command source to wrap
     */
    public VelocityAudience(CommandSource source) {
        this.source = source;
        this.id = source instanceof Player player ? player.getUniqueId() : null;
    }

    @Override
    public void send(Component component) {
        if (source == null || component == null) {
            return;
        }
        source.sendMessage(component);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return source instanceof Player player ? player.getUsername() : null;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return source != null && source.hasPermission(permission);
    }
}
