package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;

/**
 * NeoForge-specific log builder with player helpers.
 */
public class LogBuilder extends LogBuilderCore {
    private final Logger logger;

    public LogBuilder(Logger logger, LogLevel level) {
        super(logger.getCore(), level);
        this.logger = logger;
    }

    @Override
    public LogBuilder to(Audience audience) {
        super.to(audience);
        return this;
    }

    @Override
    public LogBuilder target(LogTarget target) {
        super.target(target);
        return this;
    }

    public LogBuilder to(ServerPlayer player) {
        super.to(logger.wrapAudience(player));
        return this;
    }

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

    public LogBuilder recipient(ServerPlayer player) {
        super.recipient(logger.wrapAudience(player));
        return this;
    }

    public LogBuilder to(CommandSourceStack source) {
        super.to(logger.wrapAudience(source));
        return this;
    }

    public LogBuilder to(CommandSourceStack source, boolean broadcastToOps) {
        super.to(logger.wrapAudience(source, broadcastToOps));
        return this;
    }

    public LogBuilder toError(CommandSourceStack source) {
        super.to(logger.wrapErrorAudience(source));
        return this;
    }

    public LogBuilder recipient(CommandSourceStack source) {
        super.recipient(logger.wrapAudience(source));
        return this;
    }

    public LogBuilder recipientError(CommandSourceStack source) {
        super.recipient(logger.wrapErrorAudience(source));
        return this;
    }

    @Override
    public LogBuilder toAudiences(Collection<? extends Audience> audiences) {
        super.toAudiences(audiences);
        return this;
    }

    @Override
    public LogBuilder toAll() {
        super.toAll();
        return this;
    }

    @Override
    public LogBuilder toConsole() {
        super.toConsole();
        return this;
    }

    @Override
    public LogBuilder noPrefix() {
        super.noPrefix();
        return this;
    }

    @Override
    public LogBuilder prefixMode(PrefixMode mode) {
        super.prefixMode(mode);
        return this;
    }

    @Override
    public LogBuilder args(Object... args) {
        super.args(args);
        return this;
    }

    @Override
    public LogBuilder placeholders(Map<String, Object> placeholders) {
        super.placeholders(placeholders);
        return this;
    }

    @Override
    public LogBuilder withResolvers(TagResolver... resolvers) {
        super.withResolvers(resolvers);
        return this;
    }
}
