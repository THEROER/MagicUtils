package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * Core prefixed logger with shared formatting logic.
 */
public class PrefixedLoggerCore {
    @Getter
    private final LoggerCore logger;
    @Getter
    private final String name;
    @Getter
    private final String prefix;
    @Getter @Setter
    private boolean enabled;

    /**
     * Creates a prefixed logger core.
     *
     * @param logger parent logger core
     * @param name prefixed logger name
     * @param prefix prefix string
     */
    public PrefixedLoggerCore(LoggerCore logger, String name, String prefix) {
        this.logger = logger;
        this.name = name;
        this.prefix = prefix;
        this.enabled = true;
    }

    /**
     * Applies prefix formatting to a message.
     *
     * @param message original message
     * @return formatted message
     */
    public Object formatMessage(Object message) {
        if (message instanceof String) {
            return prefix + " " + message;
        }
        return message;
    }

    /**
     * Sends a message using the prefixed logger.
     *
     * @param level log level
     * @param message message to send
     * @param audience direct audience
     * @param audiences collection of audiences
     * @param target log target
     * @param broadcast whether to broadcast to all players
     * @param placeholders placeholder arguments
     */
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

    /**
     * Creates an INFO level builder.
     *
     * @return log builder
     */
    public LogBuilderCore log() {
        return new PrefixedLogBuilderCore(logger, LogLevel.INFO);
    }

    /**
     * Creates an INFO builder with prefix disabled.
     *
     * @return log builder
     */
    public LogBuilderCore noPrefix() {
        return new PrefixedLogBuilderCore(logger, LogLevel.INFO).noPrefix();
    }

    /**
     * Creates an INFO level builder.
     *
     * @return log builder
     */
    public LogBuilderCore info() {
        return new PrefixedLogBuilderCore(logger, LogLevel.INFO);
    }

    /**
     * Creates a WARN level builder.
     *
     * @return log builder
     */
    public LogBuilderCore warn() {
        return new PrefixedLogBuilderCore(logger, LogLevel.WARN);
    }

    /**
     * Creates an ERROR level builder.
     *
     * @return log builder
     */
    public LogBuilderCore error() {
        return new PrefixedLogBuilderCore(logger, LogLevel.ERROR);
    }

    /**
     * Creates a DEBUG level builder.
     *
     * @return log builder
     */
    public LogBuilderCore debug() {
        return new PrefixedLogBuilderCore(logger, LogLevel.DEBUG);
    }

    /**
     * Creates a SUCCESS level builder.
     *
     * @return log builder
     */
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
