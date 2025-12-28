package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * Core prefixed logger with shared formatting logic.
 */
@SuppressWarnings("doclint:missing")
public class PrefixedLoggerCore {
    @Getter
    private final LoggerCore logger;
    @Getter
    private final String name;
    @Getter
    private final String prefix;
    @Getter @Setter
    private boolean enabled;

    public PrefixedLoggerCore(LoggerCore logger, String name, String prefix) {
        this.logger = logger;
        this.name = name;
        this.prefix = prefix;
        this.enabled = true;
    }

    public Object formatMessage(Object message) {
        if (message instanceof String) {
            return prefix + " " + message;
        }
        return message;
    }

    public void send(LogLevel level,
                     Object message,
                     Audience audience,
                     Collection<? extends Audience> audiences,
                     LogTarget target,
                     boolean broadcast,
                     Object... placeholders) {
        if (!enabled) {
            return;
        }
        logger.send(level, formatMessage(message), audience, audiences, target, broadcast, placeholders);
    }

    public LogBuilderCore log() {
        return new PrefixedLogBuilderCore(logger, LogLevel.INFO);
    }

    public LogBuilderCore noPrefix() {
        return new PrefixedLogBuilderCore(logger, LogLevel.INFO).noPrefix();
    }

    public LogBuilderCore info() {
        return new PrefixedLogBuilderCore(logger, LogLevel.INFO);
    }

    public LogBuilderCore warn() {
        return new PrefixedLogBuilderCore(logger, LogLevel.WARN);
    }

    public LogBuilderCore error() {
        return new PrefixedLogBuilderCore(logger, LogLevel.ERROR);
    }

    public LogBuilderCore debug() {
        return new PrefixedLogBuilderCore(logger, LogLevel.DEBUG);
    }

    public LogBuilderCore success() {
        return new PrefixedLogBuilderCore(logger, LogLevel.SUCCESS);
    }

    private class PrefixedLogBuilderCore extends LogBuilderCore {
        PrefixedLogBuilderCore(LoggerCore logger, LogLevel level) {
            super(logger, level);
        }

        @Override
        public void send(Object message, Object... placeholders) {
            if (!enabled) {
                return;
            }
            super.send(formatMessage(message), placeholders);
        }
    }
}
