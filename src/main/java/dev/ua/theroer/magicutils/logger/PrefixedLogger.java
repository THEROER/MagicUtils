package dev.ua.theroer.magicutils.logger;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import dev.ua.theroer.magicutils.Logger;
import lombok.Getter;

/**
 * Logger instance with custom prefix
 */
@Getter
public class PrefixedLogger {
    private final String name;
    private final String prefix;
    private boolean enabled;
    
    /**
     * Creates a new PrefixedLogger with the specified name and prefix.
     * @param name the name identifier for this logger
     * @param prefix the prefix to prepend to all messages
     */
    public PrefixedLogger(String name, String prefix) {
        this.name = name;
        this.prefix = prefix;
        this.enabled = true;
    }
    
    /**
     * Sets whether this logger is enabled.
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    private String formatMessage(String message) {
        return prefix + " " + message;
    }
    
    // Universal send methods
    /**
     * Sends a message with the specified log level to console.
     * @param level the log level (INFO, WARN, ERROR, DEBUG, SUCCESS)
     * @param message the message to send (String or Component)
     */
    public void send(Logger.LogLevel level, Object message) {
        if (!enabled) return;
        if (message instanceof String) {
            Logger.send(level, formatMessage((String) message));
        } else {
            Logger.send(level, message);
        }
    }
    
    /**
     * Sends a message with the specified log level to a specific player.
     * @param level the log level (INFO, WARN, ERROR, DEBUG, SUCCESS)
     * @param message the message to send (String or Component)
     * @param player the target player to send the message to
     */
    public void send(Logger.LogLevel level, Object message, Player player) {
        if (!enabled) return;
        if (message instanceof String) {
            Logger.send(level, formatMessage((String) message), player);
        } else {
            Logger.send(level, message, player);
        }
    }
    
    /**
     * Sends a message with the specified log level with full control over delivery.
     * @param level the log level (INFO, WARN, ERROR, DEBUG, SUCCESS)
     * @param message the message to send (String or Component)
     * @param player the target player (can be null for console-only)
     * @param all if true, sends to all online players; if false, sends to specified player or console
     */
    public void send(Logger.LogLevel level, Object message, Player player, boolean all) {
        if (!enabled) return;
        if (message instanceof String) {
            Logger.send(level, formatMessage((String) message), player, all);
        } else {
            Logger.send(level, message, player, all);
        }
    }
    
    // Simple convenience methods
    /**
     * Logs an info level string message.
     * @param message the message to log
     */
    public void log(String message) { send(Logger.LogLevel.INFO, message); }
    
    /**
     * Logs an info level component message.
     * @param message the component message to log
     */
    public void log(Component message) { send(Logger.LogLevel.INFO, message); }
    
    /**
     * Logs an info level string message.
     * @param message the info message to log
     */
    public void info(String message) { send(Logger.LogLevel.INFO, message); }
    
    /**
     * Logs an info level component message.
     * @param message the info component message to log
     */
    public void info(Component message) { send(Logger.LogLevel.INFO, message); }
    
    /**
     * Logs a warning level string message.
     * @param message the warning message to log
     */
    public void warn(String message) { send(Logger.LogLevel.WARN, message); }
    
    /**
     * Logs a warning level component message.
     * @param message the warning component message to log
     */
    public void warn(Component message) { send(Logger.LogLevel.WARN, message); }
    
    /**
     * Logs an error level string message.
     * @param message the error message to log
     */
    public void error(String message) { send(Logger.LogLevel.ERROR, message); }
    
    /**
     * Logs an error level component message.
     * @param message the error component message to log
     */
    public void error(Component message) { send(Logger.LogLevel.ERROR, message); }
    
    /**
     * Logs a debug level string message.
     * @param message the debug message to log
     */
    public void debug(String message) { send(Logger.LogLevel.DEBUG, message); }
    
    /**
     * Logs a debug level component message.
     * @param message the debug component message to log
     */
    public void debug(Component message) { send(Logger.LogLevel.DEBUG, message); }
    
    /**
     * Logs a success level string message.
     * @param message the success message to log
     */
    public void success(String message) { send(Logger.LogLevel.SUCCESS, message); }
    
    /**
     * Logs a success level component message.
     * @param message the success component message to log
     */
    public void success(Component message) { send(Logger.LogLevel.SUCCESS, message); }
    
    // Fluent API
    /**
     * Creates a fluent log builder for info level.
     * @return LogBuilder for info level logging
     */
    public LogBuilder log() { return new PrefixedLogBuilder(Logger.LogLevel.INFO); }
    
    /**
     * Creates a fluent log builder for info level.
     * @return LogBuilder for info level logging
     */
    public LogBuilder info() { return new PrefixedLogBuilder(Logger.LogLevel.INFO); }
    
    /**
     * Creates a fluent log builder for warn level.
     * @return LogBuilder for warn level logging
     */
    public LogBuilder warn() { return new PrefixedLogBuilder(Logger.LogLevel.WARN); }
    
    /**
     * Creates a fluent log builder for error level.
     * @return LogBuilder for error level logging
     */
    public LogBuilder error() { return new PrefixedLogBuilder(Logger.LogLevel.ERROR); }
    
    /**
     * Creates a fluent log builder for debug level.
     * @return LogBuilder for debug level logging
     */
    public LogBuilder debug() { return new PrefixedLogBuilder(Logger.LogLevel.DEBUG); }
    
    /**
     * Creates a fluent log builder for success level.
     * @return LogBuilder for success level logging
     */
    public LogBuilder success() { return new PrefixedLogBuilder(Logger.LogLevel.SUCCESS); }
    
    private class PrefixedLogBuilder extends LogBuilder {
        public PrefixedLogBuilder(Logger.LogLevel level) {
            super(level);
        }
        
        @Override
        public void send(String message) {
            if (!enabled) return;
            super.send(formatMessage(message));
        }
    }
}