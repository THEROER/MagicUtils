package examples.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Demonstrates logger initialisation, sub-loggers, and smart parsing.
 */
public final class LoggerExample {

    private LoggerExample() {
    }

    public static void bootstrap(JavaPlugin plugin, ConfigManager configManager) {
        Logger.init(plugin, configManager);

        Logger.info("<green>MagicUtils logger ready.</green>");
        Logger.warn("&eLegacy colour codes are converted automatically.");

        PrefixedLogger database = Logger.prefixed("database");
        database.debug("Connecting to {host}", Placeholder.parsed("host", "redis.example"));
        database.error("Connection failed", TagResolver.placeholder("reason", "timeout"));

        Logger.setAutoLocalization(true);
    }
}
