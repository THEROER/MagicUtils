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
@SuppressWarnings("doclint:missing")
public class LogBuilder extends LogBuilderCore<LogBuilder> {
    private final Logger logger;

    public LogBuilder(Logger logger, LogLevel level) {
        super(logger.getCore(), level);
        this.logger = logger;
    }

    public LogBuilder to(Player player) {
        super.to(logger.wrapAudience(player));
        return this;
    }

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

    public LogBuilder to(CommandSender sender) {
        super.recipient(logger.wrapAudience(sender));
        return this;
    }

    public LogBuilder recipient(CommandSender sender) {
        super.recipient(logger.wrapAudience(sender));
        return this;
    }

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
