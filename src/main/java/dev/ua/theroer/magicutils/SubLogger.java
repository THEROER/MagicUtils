package dev.ua.theroer.magicutils;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sub-logger class that adds a prefix to all log messages and can be individually enabled/disabled
 */
@Getter
public class SubLogger {
    private final String name;
    private final String prefix;
    private boolean enabled;
    
    /**
     * Creates a new sub-logger with the given name
     * @param name the name of the sub-logger (used for configuration)
     * @param prefix the prefix to add to all messages
     */
    public SubLogger(String name, String prefix) {
        this.name = name;
        this.prefix = prefix;
        this.enabled = true;
    }
    
    /**
     * Creates a new sub-logger with the given name and auto-generated prefix
     * @param name the name of the sub-logger
     */
    public SubLogger(String name) {
        this(name, "[" + name + "]");
    }
    
    /**
     * Sets whether this sub-logger is enabled
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Formats a message with this sub-logger's prefix
     * @param message the message to format
     * @return the formatted message
     */
    private String formatMessage(String message) {
        return prefix + " " + message;
    }
    
    // Log methods
    
    public void log(String message) {
        if (enabled) Logger.log(formatMessage(message));
    }
    
    public void log(Component message) {
        if (enabled) Logger.log(message);
    }
    
    public void log(Player player, String message) {
        if (enabled) Logger.log(player, formatMessage(message));
    }
    
    public void log(Player player, Component message) {
        if (enabled) Logger.log(player, message);
    }
    
    public void log(String message, boolean all) {
        if (enabled) Logger.log(formatMessage(message), all);
    }
    
    public void log(Component message, boolean all) {
        if (enabled) Logger.log(message, all);
    }
    
    public void log(Player player, String message, boolean all) {
        if (enabled) Logger.log(player, formatMessage(message), all);
    }
    
    public void log(Player player, Component message, boolean all) {
        if (enabled) Logger.log(player, message, all);
    }
    
    // Info methods
    
    public void info(String message) {
        if (enabled) Logger.info(formatMessage(message));
    }
    
    public void info(Component message) {
        if (enabled) Logger.info(message);
    }
    
    public void info(Player player, String message) {
        if (enabled) Logger.info(player, formatMessage(message));
    }
    
    public void info(Player player, Component message) {
        if (enabled) Logger.info(player, message);
    }
    
    public void info(String message, boolean all) {
        if (enabled) Logger.info(formatMessage(message), all);
    }
    
    public void info(Component message, boolean all) {
        if (enabled) Logger.info(message, all);
    }
    
    public void info(Player player, String message, boolean all) {
        if (enabled) Logger.info(player, formatMessage(message), all);
    }
    
    public void info(Player player, Component message, boolean all) {
        if (enabled) Logger.info(player, message, all);
    }
    
    // Warn methods
    
    public void warn(String message) {
        if (enabled) Logger.warn(formatMessage(message));
    }
    
    public void warn(Component message) {
        if (enabled) Logger.warn(message);
    }
    
    public void warn(Player player, String message) {
        if (enabled) Logger.warn(player, formatMessage(message));
    }
    
    public void warn(Player player, Component message) {
        if (enabled) Logger.warn(player, message);
    }
    
    public void warn(String message, boolean all) {
        if (enabled) Logger.warn(formatMessage(message), all);
    }
    
    public void warn(Component message, boolean all) {
        if (enabled) Logger.warn(message, all);
    }
    
    public void warn(Player player, String message, boolean all) {
        if (enabled) Logger.warn(player, formatMessage(message), all);
    }
    
    public void warn(Player player, Component message, boolean all) {
        if (enabled) Logger.warn(player, message, all);
    }
    
    // Error methods
    
    public void error(String message) {
        if (enabled) Logger.error(formatMessage(message));
    }
    
    public void error(Component message) {
        if (enabled) Logger.error(message);
    }
    
    public void error(Player player, String message) {
        if (enabled) Logger.error(player, formatMessage(message));
    }
    
    public void error(Player player, Component message) {
        if (enabled) Logger.error(player, message);
    }
    
    public void error(String message, boolean all) {
        if (enabled) Logger.error(formatMessage(message), all);
    }
    
    public void error(Component message, boolean all) {
        if (enabled) Logger.error(message, all);
    }
    
    public void error(Player player, String message, boolean all) {
        if (enabled) Logger.error(player, formatMessage(message), all);
    }
    
    public void error(Player player, Component message, boolean all) {
        if (enabled) Logger.error(player, message, all);
    }
    
    // Debug methods
    
    public void debug(String message) {
        if (enabled) Logger.debug(formatMessage(message));
    }
    
    public void debug(Component message) {
        if (enabled) Logger.debug(message);
    }
    
    public void debug(Player player, String message) {
        if (enabled) Logger.debug(player, formatMessage(message));
    }
    
    public void debug(Player player, Component message) {
        if (enabled) Logger.debug(player, message);
    }
    
    public void debug(String message, boolean all) {
        if (enabled) Logger.debug(formatMessage(message), all);
    }
    
    public void debug(Component message, boolean all) {
        if (enabled) Logger.debug(message, all);
    }
    
    public void debug(Player player, String message, boolean all) {
        if (enabled) Logger.debug(player, formatMessage(message), all);
    }
    
    public void debug(Player player, Component message, boolean all) {
        if (enabled) Logger.debug(player, message, all);
    }
    
    // Success methods
    
    public void success(String message) {
        if (enabled) Logger.success(formatMessage(message));
    }
    
    public void success(Component message) {
        if (enabled) Logger.success(message);
    }
    
    public void success(Player player, String message) {
        if (enabled) Logger.success(player, formatMessage(message));
    }
    
    public void success(Player player, Component message) {
        if (enabled) Logger.success(player, message);
    }
    
    public void success(String message, boolean all) {
        if (enabled) Logger.success(formatMessage(message), all);
    }
    
    public void success(Component message, boolean all) {
        if (enabled) Logger.success(message, all);
    }
    
    public void success(Player player, String message, boolean all) {
        if (enabled) Logger.success(player, formatMessage(message), all);
    }
    
    public void success(Player player, Component message, boolean all) {
        if (enabled) Logger.success(player, message, all);
    }
}