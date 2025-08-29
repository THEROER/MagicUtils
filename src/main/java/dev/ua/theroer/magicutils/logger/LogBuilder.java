package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.Logger.LogLevel;
import dev.ua.theroer.magicutils.Logger.Target;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Fluent API builder for logging messages with advanced options.
 * This builder provides a flexible way to construct and send log messages
 * with various targeting options, formatting, and customization.
 */
@Getter
public class LogBuilder {
    private final LogLevel level;
    private Target target;
    private boolean broadcast = false;
    private Player player;
    @Singular("recipient")
    private List<CommandSender> recipients;
    private Logger.PrefixMode prefixOverride;
    private boolean noPrefix = false;
    @Singular("resolver")
    private List<TagResolver> tagResolvers;
    private Object[] args;
    private Map<String, Object> placeholders;

    /**
     * Creates a new LogBuilder with the specified log level.
     * 
     * @param level the log level for messages built by this instance
     */
    public LogBuilder(LogLevel level) {
        this.level = level;
        this.recipients = new java.util.ArrayList<>();
        this.tagResolvers = new java.util.ArrayList<>();
    }

    /**
     * Sets the target for this message.
     * 
     * @param target where to send the message (CHAT, CONSOLE, BOTH)
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder target(Target target) {
        this.target = target;
        return this;
    }

    /**
     * Sets the target player for this message.
     * 
     * @param player the player to send the message to
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder to(Player player) {
        this.player = player;
        return this;
    }

    /**
     * Sets multiple target players for this message.
     * 
     * @param players the players to send the message to
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder to(Player... players) {
        for (Player p : players) {
            if (p != null) {
                this.recipients.add(p);
            }
        }
        return this;
    }

    /**
     * Sets a collection of target players for this message.
     * 
     * @param players the collection of players to send the message to
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder to(Collection<? extends Player> players) {
        if (players != null) {
            this.recipients.addAll(players);
        }
        return this;
    }

    /**
     * Adds a recipient to the message.
     * 
     * @param sender the command sender to add as recipient
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder recipient(CommandSender sender) {
        if (sender != null) {
            this.recipients.add(sender);
        }
        return this;
    }

    /**
     * Sets a collection of command senders as recipients.
     * 
     * @param senders the collection of command senders
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder toSenders(Collection<? extends CommandSender> senders) {
        if (senders != null) {
            this.recipients.addAll(senders);
        }
        return this;
    }

    /**
     * Configures the message to be sent to all online players.
     * 
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder toAll() {
        this.broadcast = true;
        return this;
    }

    /**
     * Configures the message to be sent only to console.
     * 
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder toConsole() {
        this.target = Target.CONSOLE;
        return this;
    }

    /**
     * Disables prefix for this message.
     * 
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder noPrefix() {
        this.noPrefix = true;
        this.prefixOverride = Logger.PrefixMode.NONE;
        return this;
    }

    /**
     * Sets a custom prefix mode for this message.
     * 
     * @param mode the prefix mode to use
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder prefixMode(Logger.PrefixMode mode) {
        this.prefixOverride = mode;
        return this;
    }

    /**
     * Sets formatting arguments for the message.
     * 
     * @param args the formatting arguments
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder args(Object... args) {
        this.args = args;
        return this;
    }

    /**
     * Sets placeholders for the message.
     * 
     * @param placeholders the placeholders map
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder placeholders(Map<String, Object> placeholders) {
        this.placeholders = placeholders;
        return this;
    }

    /**
     * Adds tag resolvers for MiniMessage parsing.
     * 
     * @param resolvers the tag resolvers to add
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder withResolvers(TagResolver... resolvers) {
        if (resolvers != null) {
            for (TagResolver resolver : resolvers) {
                if (resolver != null) {
                    this.tagResolvers.add(resolver);
                }
            }
        }
        return this;
    }

    /**
     * Sends the message with current settings.
     * 
     * @param message the message to send (any Object)
     */
    public void send(Object message) {
        // Apply prefix override if set
        if (prefixOverride != null) {
            Logger.PrefixMode savedChatMode = null;
            Logger.PrefixMode savedConsoleMode = null;
            
            try {
                // TODO: Save current modes when getters are available
                // savedChatMode = Logger.getChatPrefixMode();
                // savedConsoleMode = Logger.getConsolePrefixMode();
                
                Logger.setChatPrefixMode(prefixOverride);
                Logger.setConsolePrefixMode(prefixOverride);
                
                performSend(message);
            } finally {
                // Restore original modes
                if (savedChatMode != null) {
                    Logger.setChatPrefixMode(savedChatMode);
                }
                if (savedConsoleMode != null) {
                    Logger.setConsolePrefixMode(savedConsoleMode);
                }
            }
        } else {
            performSend(message);
        }
    }

    /**
     * Formats and sends a message with current settings.
     * 
     * @param format the format string
     * @param args the formatting arguments
     */
    public void sendf(String format, Object... args) {
        send(String.format(format, args));
    }

    /**
     * Internal method to perform the actual send.
     */
    private void performSend(Object message) {
        // Determine target
        Target finalTarget = target != null ? target : Logger.getDefaultTarget();
        
        // Prepare recipients collection
        Collection<? extends Player> playerRecipients = null;
        if (!recipients.isEmpty()) {
            List<Player> players = new java.util.ArrayList<>();
            for (CommandSender sender : recipients) {
                if (sender instanceof Player) {
                    players.add((Player) sender);
                }
            }
            if (!players.isEmpty()) {
                playerRecipients = players;
            }
        }
        
        // Send using the universal method
        Logger.send(level, message, player, playerRecipients, finalTarget, broadcast);
    }
}