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
     * Basic usage examples with the generated LoggerGen class.
     */
    public void basicUsageExamples() {
        // Simple logging to default target (BOTH by default)
        LoggerGen.info("Server started successfully");
        LoggerGen.error(new RuntimeException("Test exception"));
        LoggerGen.debug(Map.of("tps", 19.5, "memory", "512MB"));
        
        // Formatted messages
        LoggerGen.warn("Low memory: %d MB free", 256);
        LoggerGen.success("Loaded %d players in %d ms", 15, 1234);
        
        // Console only
        LoggerGen.infoConsole("Console-only message");
        LoggerGen.errorConsole("Error details for administrators");
        
        // Broadcast to all players
        LoggerGen.infoAll("Server will restart in 5 minutes");
        LoggerGen.warnAll("Maintenance mode activated");
    }
    
    /**
     * Player-targeted logging examples.
     * 
     * @param player the target player
     * @param players list of players
     */
    public void playerTargetedExamples(Player player, List<Player> players) {
        // Send to specific player
        LoggerGen.info(player, "Welcome to the server!");
        LoggerGen.error(player, "You don't have permission for this command");
        LoggerGen.success(player, "Quest completed! Reward: %d gold", 100);
        
        // Send to multiple players
        LoggerGen.warn(players, "PvP will be enabled in 30 seconds");
        LoggerGen.debug(players, "Debug mode activated for your session");
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
        PrefixedLoggerGen.info(auth, "Authentication service started");
        PrefixedLoggerGen.error(auth, "Failed to validate token");
        
        PrefixedLoggerGen.debug(db, "Executing query: SELECT * FROM users");
        PrefixedLoggerGen.success(db, "Database connection established");
        
        PrefixedLoggerGen.warn(api, "Rate limit approaching: %d/%d", 450, 500);
    }
    
    /**
     * Fluent API examples for advanced usage.
     * 
     * @param player target player
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
            .prefixMode(Logger.PrefixMode.SHORT)
            .send(new IllegalStateException("Invalid game state"));
            
        // Broadcast with formatting
        Logger.success()
            .toAll()
            .sendf("<rainbow>Event started! %d players participating</rainbow>", 25);
            
        // Complex targeting
        Logger.debug()
            .to(moderators)
            .target(Logger.Target.BOTH)
            .send(Map.of(
                "reporter", player.getName(),
                "location", player.getLocation(),
                "gamemode", player.getGameMode()
            ));
    }
    
    /**
     * Examples with localization support.
     * 
     * @param player target player
     */
    public void localizationExamples(Player player) {
        // Direct localization key (requires @ prefix)
        LoggerGen.info(player, "@welcome.message");
        LoggerGen.error(player, "@error.insufficient_permissions");
        
        // Mixed content with legacy and MiniMessage
        LoggerGen.success(player, "&aSuccess! <gold>You earned <bold>%d</bold> points!</gold>", 50);
        
        // Gradient examples (if enabled in config)
        LoggerGen.infoAll("<gradient:#ff0000:#00ff00>Rainbow announcement!</gradient>");
    }
    
    /**
     * Advanced examples showing special features.
     */
    public void advancedExamples() {
        // Object logging - any object works
        LoggerGen.debug(new Object() {
            public String toString() {
                return "CustomObject[id=123, status=ACTIVE]";
            }
        });
        
        // Collections and maps are formatted nicely
        LoggerGen.info(List.of("item1", "item2", "item3"));
        LoggerGen.debug(Map.of(
            "server", "survival",
            "players", 45,
            "tps", 19.97
        ));
        
        // Runtime configuration changes
        Logger.setPrefixModes(Logger.PrefixMode.SHORT, Logger.PrefixMode.NONE);
        Logger.setDefaultTarget(Logger.Target.CONSOLE);
        Logger.setConsoleStripFormatting(true);
        
        // Direct universal send for special cases
        Logger.send(
            Logger.LogLevel.INFO,
            "Special message",
            null, // no single player
            List.of(), // empty recipients
            Logger.Target.CONSOLE, // console only
            false // no broadcast
        );
    }
    
    /**
     * Migration guide - shows old vs new API.
     * 
     * @param player target player
     */
    public void migrationExamples(Player player) {
        // OLD: LoggerGen.info("message")
        // NEW:
        LoggerGen.info("message");
        
        // OLD: LoggerGen.error(exception)
        // NEW:
        Exception e = new Exception("test");
        LoggerGen.error(e);
        
        // OLD: Logger.broadcast("message")
        // NEW:
        LoggerGen.broadcast("message");
        // or
        LoggerGen.infoAll("message");
        
        // OLD: prefixedLoggerGen.info("message")
        // NEW:
        PrefixedLogger module = Logger.withPrefix("Module");
        PrefixedLoggerGen.info(module, "message");
        
        // OLD: Logger.send(LogLevel.INFO, "message", player)
        // NEW:
        LoggerGen.info(player, "message");
    }
}