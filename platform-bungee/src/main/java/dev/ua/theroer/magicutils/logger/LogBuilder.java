package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import java.util.Collection;
import net.md_5.bungee.api.connection.ProxiedPlayer;

/**
 * BungeeCord-specific log builder with player helpers.
 *
 * <p>The shared fluent methods ({@code to(Audience)}, {@code target},
 * {@code noPrefix}, ...) are inherited from {@link LogBuilderCore} and already
 * return {@code LogBuilder} thanks to its self type; this class only adds the
 * Bungee-typed recipient overloads.
 */
public class LogBuilder extends LogBuilderCore<LogBuilder> {
    private final Logger logger;

    /**
     * Creates a log builder for the specified level.
     *
     * @param logger parent logger
     * @param level log level
     */
    public LogBuilder(Logger logger, LogLevel level) {
        super(logger.getCore(), level);
        this.logger = logger;
    }

    /**
     * Adds a player recipient.
     *
     * @param player target player
     * @return this builder
     */
    public LogBuilder to(ProxiedPlayer player) {
        super.to(logger.wrapAudience(player));
        return this;
    }

    /**
     * Adds multiple player recipients.
     *
     * @param players target players
     * @return this builder
     */
    public LogBuilder to(ProxiedPlayer... players) {
        if (players != null) {
            for (ProxiedPlayer player : players) {
                if (player != null) {
                    super.recipient(logger.wrapAudience(player));
                }
            }
        }
        return this;
    }

    /**
     * Adds a collection of player recipients.
     *
     * @param players target players
     * @return this builder
     */
    public LogBuilder to(Collection<? extends ProxiedPlayer> players) {
        if (players != null) {
            for (ProxiedPlayer player : players) {
                if (player != null) {
                    super.recipient(logger.wrapAudience(player));
                }
            }
        }
        return this;
    }

    /**
     * Adds a player as a recipient.
     *
     * @param player target player
     * @return this builder
     */
    public LogBuilder recipient(ProxiedPlayer player) {
        super.recipient(logger.wrapAudience(player));
        return this;
    }
}
