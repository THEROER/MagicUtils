package examples.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.utils.placeholders.PlaceholderProcessor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Demonstrates registering custom placeholders for the MagicUtils logger.
 */
public final class PlaceholderDemo {

    private PlaceholderDemo() {
    }

    public static void setup(JavaPlugin plugin) {
        // Global placeholder without player context
        PlaceholderProcessor.registerGlobalPlaceholder("server-online", () ->
                String.valueOf(Bukkit.getOnlinePlayers().size()));

        // Per-plugin placeholder that depends on player context
        PlaceholderProcessor.registerLocalPlaceholder(plugin, "player-ping",
                player -> player != null ? String.valueOf(player.getPing()) : "?");

        Logger.info().send("<green>Registered custom placeholders.</green>");
    }

    public static void sendDemo(JavaPlugin plugin, Player player) {
        Logger.info()
                .to(player)
                .send("Players: {server-online}, your ping: {player-ping}");

        Logger.info().toConsole()
                .send("{server-online} players online; command executed.");
    }
}
