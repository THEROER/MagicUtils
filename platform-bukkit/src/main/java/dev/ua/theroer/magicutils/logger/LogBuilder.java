package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;

/**
 * Bukkit-specific log builder with Player/CommandSender helpers.
 */
@SuppressWarnings("doclint:missing")
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
