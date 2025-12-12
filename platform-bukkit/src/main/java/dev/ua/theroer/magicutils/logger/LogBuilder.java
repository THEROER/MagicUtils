package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import lombok.Getter;
import lombok.Singular;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent API builder for logging messages with advanced options.
 * This builder provides a flexible way to construct and send log messages
 * with various targeting options, formatting, and customization.
 */
public class LogBuilder {
    private final LogLevel level;
    @Getter
    private LogTarget target;
    @Getter
    private boolean broadcast = false;
    @Getter
    private Player player;
    @Singular("recipient")
    private List<CommandSender> recipients;
    @Getter
    private PrefixMode prefixOverride;
    @Getter
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
        this.recipients = new ArrayList<>();
        this.tagResolvers = new ArrayList<>();
    }

    /**
     * Snapshot of current recipients (players or senders).
     *
     * @return copy of recipients list
     */
    public List<CommandSender> getRecipients() {
        return new ArrayList<>(recipients);
    }

    /**
     * Additional MiniMessage tag resolvers attached to this builder.
     *
     * @return copy of resolver list
     */
    public List<TagResolver> getTagResolvers() {
        return new ArrayList<>(tagResolvers);
    }

    /**
     * Formatting arguments passed to {@link #args(Object...)}.
     *
     * @return defensive copy of arguments or null
     */
    public Object[] getArgs() {
        return args != null ? args.clone() : null;
    }

    /**
     * Named placeholders provided via {@link #placeholders(Map)}.
     *
     * @return copy of placeholders or null
     */
    public Map<String, Object> getPlaceholders() {
        return placeholders != null ? new HashMap<>(placeholders) : null;
    }

    /**
     * Sets the target for this message.
     * 
     * @param target where to send the message (CHAT, CONSOLE, BOTH)
     * @return this LogBuilder instance for method chaining
     */
public LogBuilder target(LogTarget target) {
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
     * Sets the target command sender for this message.
     * 
     * @param sender the command sender to send the message to
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder to(CommandSender sender) {
        if (sender != null) {
            this.recipients.add(sender);
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
        this.target = LogTarget.CONSOLE;
        return this;
    }

    /**
     * Disables prefix for this message.
     * 
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder noPrefix() {
        this.noPrefix = true;
        this.prefixOverride = PrefixMode.NONE;
        return this;
    }

    /**
     * Sets a custom prefix mode for this message.
     * 
     * @param mode the prefix mode to use
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder prefixMode(PrefixMode mode) {
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
        this.args = args != null ? args.clone() : null;
        return this;
    }

    /**
     * Sets placeholders for the message.
     * 
     * @param placeholders the placeholders map
     * @return this LogBuilder instance for method chaining
     */
    public LogBuilder placeholders(Map<String, Object> placeholders) {
        this.placeholders = placeholders != null ? new HashMap<>(placeholders) : null;
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
     * @param placeholders the placeholders to apply to the message
     */
    public void send(Object message, Object... placeholders) {
        // Apply prefix override if set
        if (prefixOverride != null) {
            PrefixMode savedChatMode = null;
            PrefixMode savedConsoleMode = null;

            try {
                savedChatMode = Logger.getConfig().getChatPrefixMode();
                savedConsoleMode = Logger.getConfig().getConsolePrefixMode();

                Logger.setChatPrefixMode(prefixOverride);
                Logger.setConsolePrefixMode(prefixOverride);

                performSend(message, placeholders);
            } finally {
                if (savedChatMode != null) {
                    Logger.setChatPrefixMode(savedChatMode);
                }
                if (savedConsoleMode != null) {
                    Logger.setConsolePrefixMode(savedConsoleMode);
                }
            }
        } else {
            performSend(message, placeholders);
        }
    }

    /**
     * Internal method to perform the actual send.
     */
    private void performSend(Object message, Object... placeholders) {
        // Determine target
        LogTarget finalTarget = target != null ? target : Logger.getDefaultTarget();

        // Prepare recipients collection
        Collection<? extends Player> playerRecipients = null;
        if (!recipients.isEmpty()) {
            List<Player> players = new ArrayList<>();
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
        Logger.send(level, message, player, playerRecipients, finalTarget, broadcast, placeholders);
    }
}
