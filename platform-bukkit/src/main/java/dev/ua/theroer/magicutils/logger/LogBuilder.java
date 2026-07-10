package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Bukkit-specific log builder with Player/CommandSender helpers.
 *
 * <p>The shared fluent methods ({@code to(Audience)}, {@code target},
 * {@code noPrefix}, ...) are inherited from {@link LogBuilderCore} and already
 * return {@code LogBuilder} thanks to its self type; this class only adds the
 * Bukkit-typed recipient overloads.
 */
public class LogBuilder extends LogBuilderCore<LogBuilder> {
    private final Logger logger;

    /**
     * Creates a log builder for the given level.
     *
     * @param logger parent logger
     * @param level log level
     */
    public LogBuilder(Logger logger, LogLevel level) {
        super(logger.getCore(), level);
        this.logger = logger;
    }

    /**
     * Sets a player as the direct audience.
     *
     * @param player target player
     * @return this builder
     */
    public LogBuilder to(Player player) {
        super.to(logger.wrapAudience(player));
        return this;
    }

    /**
     * Adds several players as recipients.
     *
     * @param players target players
     * @return this builder
     */
    public LogBuilder to(Player... players) {
        if (players != null) {
            for (Player player : players) {
                if (player != null) {
                    super.recipient(logger.wrapAudience(player));
                }
            }
        }
        return this;
    }

    /**
     * Adds a collection of players as recipients.
     *
     * @param players target players
     * @return this builder
     */
    public LogBuilder to(Collection<? extends Player> players) {
        if (players != null) {
            for (Player player : players) {
                if (player != null) {
                    super.recipient(logger.wrapAudience(player));
                }
            }
        }
        return this;
    }

    /**
     * Adds a command sender (player or console) as a recipient.
     *
     * @param sender command sender
     * @return this builder
     */
    public LogBuilder to(CommandSender sender) {
        super.recipient(logger.wrapAudience(sender));
        return this;
    }

    /**
     * Adds a command sender as a recipient.
     *
     * @param sender command sender
     * @return this builder
     */
    public LogBuilder recipient(CommandSender sender) {
        super.recipient(logger.wrapAudience(sender));
        return this;
    }

    /**
     * Adds a collection of command senders as recipients.
     *
     * @param senders command senders
     * @return this builder
     */
    public LogBuilder toSenders(Collection<? extends CommandSender> senders) {
        if (senders != null) {
            for (CommandSender sender : senders) {
                if (sender != null) {
                    super.recipient(logger.wrapAudience(sender));
                }
            }
        }
        return this;
    }
}
