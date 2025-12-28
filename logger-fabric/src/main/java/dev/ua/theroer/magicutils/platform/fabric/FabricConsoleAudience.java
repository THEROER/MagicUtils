package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.ComponentPrefixStripper;
import dev.ua.theroer.magicutils.logger.ConsoleMessageParser;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;

/**
 * Console audience that logs through PlatformLogger.
 */
public final class FabricConsoleAudience implements Audience {
    private static final ANSIComponentSerializer ANSI = ANSIComponentSerializer.ansi();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final PlatformLogger logger;
    private final String baseLoggerName;

    /**
     * Creates a console audience with default logger name handling.
     *
     * @param logger platform logger
     */
    public FabricConsoleAudience(PlatformLogger logger) {
        this(logger, null);
    }

    /**
     * Creates a console audience that logs with a base logger name.
     *
     * @param logger platform logger
     * @param baseLoggerName base name for sub-loggers
     */
    public FabricConsoleAudience(PlatformLogger logger, String baseLoggerName) {
        this.logger = logger;
        this.baseLoggerName = baseLoggerName != null && !baseLoggerName.isBlank() ? baseLoggerName : null;
    }

    @Override
    public void send(Component component) {
        if (component == null) {
            return;
        }
        String plain = PLAIN.serialize(component);
        if (baseLoggerName == null) {
            if (logger != null) {
                logger.info(plain);
            }
            return;
        }
        ConsoleMessageParser.ParsedMessage parsed = ConsoleMessageParser.parse(plain);
        Component stripped = ComponentPrefixStripper.stripPrefix(component, parsed.prefixText());
        String loggerName = buildLoggerName(baseLoggerName, parsed.subLogger());
        Logger slf4j = LoggerFactory.getLogger(loggerName);
        String rendered = ANSI.serialize(stripped);
        logWithLevel(slf4j, parsed.level(), rendered);
    }

    private String buildLoggerName(String baseName, String subLogger) {
        if (subLogger == null || subLogger.isBlank()) {
            return baseName;
        }
        return baseName + "-" + normalizeSegment(subLogger);
    }

    private String normalizeSegment(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.replaceAll("\\s+", "-");
    }

    private void logWithLevel(Logger slf4j, LogLevel level, String message) {
        if (slf4j == null) {
            return;
        }
        if (level == null) {
            slf4j.info(message);
            return;
        }
        switch (level) {
            case WARN -> slf4j.warn(message);
            case ERROR -> slf4j.error(message);
            case DEBUG -> {
                if (slf4j.isDebugEnabled()) {
                    slf4j.debug(message);
                }
            }
            case TRACE -> {
                if (slf4j.isTraceEnabled()) {
                    slf4j.trace(message);
                } else if (slf4j.isDebugEnabled()) {
                    slf4j.debug(message);
                }
            }
            case SUCCESS, INFO -> slf4j.info(message);
        }
    }

}
