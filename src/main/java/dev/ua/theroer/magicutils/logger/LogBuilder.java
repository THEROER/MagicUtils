package dev.ua.theroer.magicutils.logger;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.Logger.LogLevel;

/**
 * Fluent API builder for logging messages
 */
public class LogBuilder {
    private final LogLevel level;
    private Object message;
    private Player player;
    private boolean sendToAll = false;
    
    /**
     * Creates a new LogBuilder with the specified log level.
     * @param level the log level for messages built by this instance
     */
    public LogBuilder(LogLevel level) {
        this.level = level;
    }
    
    /**
     * Sets the message to log.
     * @param message the string message to log
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder message(String message) {
        this.message = message;
        return this;
    }
    
    /**
     * Sets the message to log.
     * @param message the component message to log
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder message(Component message) {
        this.message = message;
        return this;
    }
    
    /**
     * Sets the target player for this message.
     * @param player the player to send the message to
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder to(Player player) {
        this.player = player;
        return this;
    }
    
    /**
     * Configures the message to be sent to all online players.
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder toAll() {
        this.sendToAll = true;
        return this;
    }
    
    /**
     * Sends the message with current settings.
     * @throws IllegalStateException if no message has been set
     */
    public void send() {
        if (message == null) {
            throw new IllegalStateException(InternalMessages.ERR_MESSAGE_NOT_SET.get());
        }
        Logger.send(level, message, player, sendToAll);
    }
    
    /**
     * Directly sends a string message with current settings.
     * @param message the string message to send
     */
    public void send(String message) {
        this.message = message;
        send();
    }
    
    /**
     * Directly sends a component message with current settings.
     * @param message the component message to send
     */
    public void send(Component message) {
        this.message = message;
        send();
    }
}