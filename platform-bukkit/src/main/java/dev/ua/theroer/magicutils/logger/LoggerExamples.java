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

    /**
     * Default constructor for LoggerExamples.
     */
    public LoggerExamples() {
    }

    /**
     * Basic usage examples with the generated Logger class.
     */
    public void basicUsageExamples() {
        // Simple logging to default target (BOTH by default)
        Logger.info("Server started successfully");
        Logger.error(new RuntimeException("Test exception"));
        Logger.debug(Map.of("tps", 19.5, "memory", "512MB"));

        // Formatted messages
        Logger.warn("Low memory: %d MB free", 256);
        Logger.success("Loaded %d players in %d ms", 15, 1234);

        // Console only
        Logger.infoConsole("Console-only message");
        Logger.errorConsole("Error details for administrators");

        // Broadcast to all players
        Logger.infoAll("Server will restart in 5 minutes");
        Logger.warnAll("Maintenance mode activated");
    }

    /**
     * Player-targeted logging examples.
     * 
     * @param player  the target player
     * @param players list of players
     */
    public void playerTargetedExamples(Player player, List<Player> players) {
        // Send to specific player
        Logger.info(player, "Welcome to the server!");
        Logger.error(player, "You don't have permission for this command");
        Logger.success(player, "Quest completed! Reward: %d gold", 100);

        // Send to multiple players
        Logger.warn(players, "PvP will be enabled in 30 seconds");
        Logger.debug(players, "Debug mode activated for your session");
    }

    /**
     * Examples using prefixed loggers for modules.
     */
    public void prefixedLoggerExamples() {
        // Create prefixed loggers for modules
        PrefixedLogger auth = Logger.withPrefix("Auth");
        PrefixedLogger db = Logger.withPrefix("Database");
        PrefixedLogger api = Logger.withPrefix("API", "[WebAPI]");

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
        Logger.info()
                .to(player)
                .send("Your request has been processed");

        // No prefix for clean messages
        Logger.warn()
                .to(moderators)
                .noPrefix()
                .send("&cSuspicious activity detected from player: " + player.getName());

        // Console only with custom formatting
        Logger.error()
                .toConsole()
                .prefixMode(PrefixMode.SHORT)
                .send(new IllegalStateException("Invalid game state"));

        // Broadcast with formatting
        Logger.success()
                .toAll()
                .send("<rainbow>Event started! %d players participating</rainbow>", 25);

        // Complex targeting
        Logger.debug()
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
        Logger.info(player, "@welcome.message");
        Logger.error(player, "@error.insufficient_permissions");

        // Mixed content with legacy and MiniMessage
        Logger.success(player, "&aSuccess! <gold>You earned <bold>%d</bold> points!</gold>", 50);

        // Gradient examples (if enabled in config)
        Logger.infoAll("<gradient:#ff0000:#00ff00>Rainbow announcement!</gradient>");
    }

    /**
     * Advanced examples showing special features.
     */
    public void advancedExamples() {
        // Object logging - any object works
        Logger.debug(new Object() {
            public String toString() {
                return "CustomObject[id=123, status=ACTIVE]";
            }
        });

        // Collections and maps are formatted nicely
        Logger.info(List.of("item1", "item2", "item3"));
        Logger.debug(Map.of(
                "server", "survival",
                "players", 45,
                "tps", 19.97));

        // Runtime configuration changes
        Logger.setChatPrefixMode(PrefixMode.SHORT);
        Logger.setConsolePrefixMode(PrefixMode.NONE);
        Logger.setDefaultTarget(LogTarget.CONSOLE);
        Logger.setConsoleStripFormatting(true);

        // Direct universal send for special cases
        Logger.send(
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
        Logger.info("message");

        // OLD: Logger.error(exception)
        // NEW:
        Exception e = new Exception("test");
        Logger.error(e);

        // OLD: Logger.broadcast("message")
        // NEW:
        Logger.broadcast("message");
        // or
        Logger.infoAll("message");

        // OLD: prefixedLogger.info("message")
        // NEW:
        PrefixedLogger module = Logger.withPrefix("Module");
        PrefixedLogger.info(module, "message");

        // OLD: Logger.send(LogLevel.INFO, "message", player)
        // NEW:
        Logger.info(player, "message");
    }
}
