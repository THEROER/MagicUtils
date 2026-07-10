package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collection;

/**
 * Fabric-specific log builder with player and command-source helpers.
 *
 * <p>The shared fluent methods ({@code to(Audience)}, {@code target},
 * {@code noPrefix}, ...) are inherited from {@link LogBuilderCore} and already
 * return {@code LogBuilder} thanks to its self type; this class only adds the
 * Fabric-typed recipient overloads.
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
    public LogBuilder to(ServerPlayer player) {
        super.to(logger.wrapAudience(player));
        return this;
    }

    /**
     * Adds multiple player recipients.
     *
     * @param players target players
     * @return this builder
     */
    public LogBuilder to(ServerPlayer... players) {
        if (players != null) {
            for (ServerPlayer player : players) {
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
    public LogBuilder to(Collection<? extends ServerPlayer> players) {
        if (players != null) {
            for (ServerPlayer player : players) {
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
    public LogBuilder recipient(ServerPlayer player) {
        super.recipient(logger.wrapAudience(player));
        return this;
    }

    /**
     * Sets command source as the direct audience.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder to(CommandSourceStack source) {
        super.to(logger.wrapAudience(source));
        return this;
    }

    /**
     * Sets command source as the direct audience, optionally broadcasting to ops.
     *
     * @param source command source
     * @param broadcastToOps whether to broadcast to ops
     * @return this builder
     */
    public LogBuilder to(CommandSourceStack source, boolean broadcastToOps) {
        super.to(logger.wrapAudience(source, broadcastToOps));
        return this;
    }

    /**
     * Sets command source as the error audience.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder toError(CommandSourceStack source) {
        super.to(logger.wrapErrorAudience(source));
        return this;
    }

    /**
     * Adds a command source as a recipient.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder recipient(CommandSourceStack source) {
        super.recipient(logger.wrapAudience(source));
        return this;
    }

    /**
     * Adds a command source as an error recipient.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder recipientError(CommandSourceStack source) {
        super.recipient(logger.wrapErrorAudience(source));
        return this;
    }
}
