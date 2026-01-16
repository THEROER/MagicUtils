package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;
import java.util.Map;

/**
 * Fabric-specific log builder with player helpers.
 */
public class LogBuilder extends LogBuilderCore {
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

    /** {@inheritDoc} */
    @Override
    public LogBuilder to(Audience audience) {
        super.to(audience);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder target(LogTarget target) {
        super.target(target);
        return this;
    }

    /**
     * Adds a player recipient.
     *
     * @param player target player
     * @return this builder
     */
    public LogBuilder to(ServerPlayerEntity player) {
        super.to(logger.wrapAudience(player));
        return this;
    }

    /**
     * Adds multiple player recipients.
     *
     * @param players target players
     * @return this builder
     */
    public LogBuilder to(ServerPlayerEntity... players) {
        if (players != null) {
            for (ServerPlayerEntity player : players) {
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
    public LogBuilder to(Collection<? extends ServerPlayerEntity> players) {
        if (players != null) {
            for (ServerPlayerEntity player : players) {
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
    public LogBuilder recipient(ServerPlayerEntity player) {
        super.recipient(logger.wrapAudience(player));
        return this;
    }

    /**
     * Sets command source as the direct audience.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder to(ServerCommandSource source) {
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
    public LogBuilder to(ServerCommandSource source, boolean broadcastToOps) {
        super.to(logger.wrapAudience(source, broadcastToOps));
        return this;
    }

    /**
     * Sets command source as the error audience.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder toError(ServerCommandSource source) {
        super.to(logger.wrapErrorAudience(source));
        return this;
    }

    /**
     * Adds a command source as a recipient.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder recipient(ServerCommandSource source) {
        super.recipient(logger.wrapAudience(source));
        return this;
    }

    /**
     * Adds a command source as an error recipient.
     *
     * @param source command source
     * @return this builder
     */
    public LogBuilder recipientError(ServerCommandSource source) {
        super.recipient(logger.wrapErrorAudience(source));
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder toAudiences(Collection<? extends Audience> audiences) {
        super.toAudiences(audiences);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder toAll() {
        super.toAll();
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder toConsole() {
        super.toConsole();
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder noPrefix() {
        super.noPrefix();
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder prefixMode(PrefixMode mode) {
        super.prefixMode(mode);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder args(Object... args) {
        super.args(args);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder placeholders(Map<String, Object> placeholders) {
        super.placeholders(placeholders);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogBuilder withResolvers(TagResolver... resolvers) {
        super.withResolvers(resolvers);
        return this;
    }
}
