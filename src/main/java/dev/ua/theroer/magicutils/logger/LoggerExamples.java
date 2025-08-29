package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import org.bukkit.entity.Player;

/**
 * Examples of all three logging approaches.
 * <p>
 * This class demonstrates the various ways to use the Logger system,
 * including simple static methods, fluent API, universal send method,
 * and prefixed loggers.
 * </p>
 * 
 * <p>Constructor usage:</p>
 * <ul>
 * <li>{@link #LoggerExamples()} - Creates a new instance for demonstration purposes</li>
 * </ul>
 */
public class LoggerExamples {
    
    /**
     * Default constructor for LoggerExamples.
     */
    public LoggerExamples() {
    }
    
    /**
     * Demonstrates various logging methods and approaches.
     * <p>
     * Shows examples of:
     * <ul>
     * <li>Simple static logging methods</li>
     * <li>Fluent API with builder pattern</li>
     * <li>Universal send method</li>
     * <li>Prefixed loggers</li>
     * </ul>
     * 
     * @param player the player to use in logging examples
     */
    public void exampleUsage(Player player) {
        // Option 1: Simple methods (backward compatible)
        Logger.info("Simple info message");
        Logger.error("Simple error message");
        Logger.debug("Debug message");
        
        // Option 2: Fluent API (Builder pattern)
        Logger.info()
            .message("Player joined")
            .to(player)
            .send();
            
        Logger.error()
            .message("Connection failed")
            .toAll()
            .send();
            
        // Direct send with message
        Logger.warn().send("Quick warning");
        
        // Option 3: Universal send method
        Logger.send(Logger.LogLevel.INFO, "Universal message");
        Logger.send(Logger.LogLevel.ERROR, "Error message", player);
        Logger.send(Logger.LogLevel.SUCCESS, "Success!", player, true); // to all
        
        // Prefixed loggers (replacement for SubLogger)
        PrefixedLogger dbLogger = Logger.create("Database");
        dbLogger.info("Connected to database");
        dbLogger.error("Query failed");
        
        // Custom prefix
        PrefixedLogger apiLogger = Logger.create("API", "[MyAPI]");
        apiLogger.debug("Processing request");
        
        // Prefixed logger with Fluent API
        dbLogger.warn()
            .message("Connection timeout")
            .to(player)
            .send();
    }
}