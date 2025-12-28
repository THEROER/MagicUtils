package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.integrations.PlaceholderApiIntegration;
import dev.ua.theroer.magicutils.logger.ExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.platform.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Placeholder engine that bridges to PlaceholderAPI when available.
 */
public final class BukkitExternalPlaceholderEngine implements ExternalPlaceholderEngine {
    private final PlaceholderApiIntegration integration;

    /**
     * Creates a PlaceholderAPI bridge for a plugin.
     *
     * @param plugin owning plugin
     */
    public BukkitExternalPlaceholderEngine(JavaPlugin plugin) {
        this.integration = plugin != null ? new PlaceholderApiIntegration(plugin) : null;
    }

    @Override
    public String apply(Audience audience, String text) {
        if (integration == null || text == null) {
            return text;
        }
        if (!(audience instanceof BukkitAudienceWrapper wrapper)) {
            return text;
        }
        CommandSender sender = wrapper.getSender();
        if (!(sender instanceof Player player)) {
            return text;
        }
        return integration.renderPlaceholders(player, text);
    }
}
