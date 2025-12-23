package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Examples of using the new unified logger system.
 * This class demonstrates all the new logging approaches after the refactoring.
 */
public class LoggerExamples {
    private final Logger logger;

    /**
     * Default constructor for LoggerExamples.
     */
    public LoggerExamples(Logger logger) {
        this.logger = logger;
    }

    /**
     * Basic usage examples with the generated Logger class.
     */
    public void basicUsageExamples() {
        // Simple logging to default target (BOTH by default)
        logger.info("Server started successfully");
        logger.error(new RuntimeException("Test exception"));
        logger.debug(Map.of("tps", 19.5, "memory", "512MB"));

        // Formatted messages
        logger.warn("Low memory: %d MB free", 256);
        logger.success("Loaded %d players in %d ms", 15, 1234);

        // Console only
        logger.infoConsole("Console-only message");
        logger.errorConsole("Error details for administrators");

        // Broadcast to all players
        logger.infoAll("Server will restart in 5 minutes");
        logger.warnAll("Maintenance mode activated");
    }

    /**
     * Player-targeted logging examples.
     * 
     * @param player  the target player
     * @param players list of players
     */
    public void playerTargetedExamples(Player player, List<Player> players) {
        // Send to specific player
        logger.info(player, "Welcome to the server!");
        logger.error(player, "You don't have permission for this command");
        logger.success(player, "Quest completed! Reward: %d gold", 100);

        // Send to multiple players
        logger.warn(players, "PvP will be enabled in 30 seconds");
        logger.debug(players, "Debug mode activated for your session");
    }

    /**
     * Examples using prefixed loggers for modules.
     */
    public void prefixedLoggerExamples() {
        // Create prefixed loggers for modules
        PrefixedLogger auth = logger.withPrefix("Auth");
        PrefixedLogger db = logger.withPrefix("Database");
        PrefixedLogger api = logger.withPrefix("API", "[WebAPI]");

        // Use with generated methods
        PrefixedLogger.info(auth, "Authentication service started");
        PrefixedLogger.error(auth, "Failed to validate token");

        PrefixedLogger.debug(db, "Executing query: SELECT * FROM users");
        PrefixedLogger.success(db, "Database connection established");

        PrefixedLogger.warn(api, "Rate limit approaching: %d/%d", 450, 500);
    }

    /**
     * Fluent API examples for advanced usage.
     * 
     * @param player     target player
     * @param moderators list of moderators
     */
    public void fluentApiExamples(Player player, List<Player> moderators) {
        // Basic fluent usage
        logger.info()
                .to(player)
                .send("Your request has been processed");

        // No prefix for clean messages
        logger.warn()
                .to(moderators)
                .noPrefix()
                .send("&cSuspicious activity detected from player: " + player.getName());

        // Console only with custom formatting
        logger.error()
                .toConsole()
                .prefixMode(PrefixMode.SHORT)
                .send(new IllegalStateException("Invalid game state"));

        // Broadcast with formatting
        logger.success()
                .toAll()
                .send("<rainbow>Event started! %d players participating</rainbow>", 25);

        // Complex targeting
        logger.debug()
                .to(moderators)
                .target(LogTarget.BOTH)
                .send(Map.of(
                        "reporter", player.getName(),
                        "location", player.getLocation(),
                        "gamemode", player.getGameMode()));
    }

    /**
     * Examples with localization support.
     * 
     * @param player target player
     */
    public void localizationExamples(Player player) {
        // Direct localization key (requires @ prefix)
        logger.info(player, "@welcome.message");
        logger.error(player, "@error.insufficient_permissions");

        // Mixed content with legacy and MiniMessage
        logger.success(player, "&aSuccess! <gold>You earned <bold>%d</bold> points!</gold>", 50);

        // Gradient examples (if enabled in config)
        logger.infoAll("<gradient:#ff0000:#00ff00>Rainbow announcement!</gradient>");
    }

    /**
     * Advanced examples showing special features.
     */
    public void advancedExamples() {
        // Object logging - any object works
        logger.debug(new Object() {
            public String toString() {
                return "CustomObject[id=123, status=ACTIVE]";
            }
        });

        // Collections and maps are formatted nicely
        logger.info(List.of("item1", "item2", "item3"));
        logger.debug(Map.of(
                "server", "survival",
                "players", 45,
                "tps", 19.97));

        // Runtime configuration changes
        logger.setChatPrefixMode(PrefixMode.SHORT);
        logger.setConsolePrefixMode(PrefixMode.NONE);
        logger.setDefaultTarget(LogTarget.CONSOLE);
        logger.setConsoleStripFormatting(true);

        // Direct universal send for special cases
        logger.send(
                LogLevel.INFO,
                "Special message",
                null, // no single player
                List.of(), // empty recipients
                LogTarget.CONSOLE, // console only
                false // no broadcast
        );
    }

    /**
     * Migration guide - shows old vs new API.
     * 
     * @param player target player
     */
    public void migrationExamples(Player player) {
        // OLD: Logger.info("message")
        // NEW:
        logger.info("message");

        // OLD: Logger.error(exception)
        // NEW:
        Exception e = new Exception("test");
        logger.error(e);

        // OLD: Logger.broadcast("message")
        // NEW:
        logger.broadcast("message");
        // or
        logger.infoAll("message");

        // OLD: prefixedLogger.info("message")
        // NEW:
        PrefixedLogger module = logger.withPrefix("Module");
        PrefixedLogger.info(module, "message");

        // OLD: Logger.send(LogLevel.INFO, "message", player)
        // NEW:
        logger.info(player, "message");
    }
}
