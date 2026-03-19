package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.ComponentPrefixStripper;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.ConsoleMessageParser;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;

/**
 * Console audience that logs through PlatformLogger.
 */
public final class FabricConsoleAudience implements StructuredConsoleAudience {
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

    @Override
    public void sendConsole(Component component, ConsoleMessageMetadata metadata) {
        if (component == null || metadata == null) {
            return;
        }
        Component stripped = ComponentPrefixStripper.stripPrefix(component, metadata.mainPrefixText());
        stripped = ComponentPrefixStripper.stripPrefix(stripped, metadata.subLoggerPrefix());
        if (baseLoggerName == null) {
            if (logger != null) {
                logWithPlatformLogger(logger, metadata.level(), PlainTextComponentSerializer.plainText().serialize(stripped));
            }
            return;
        }
        String loggerName = buildLoggerName(baseLoggerName, metadata.subLoggerName());
        Logger slf4j = LoggerFactory.getLogger(loggerName);
        String rendered = ANSI.serialize(stripped);
        logWithLevel(slf4j, metadata.level(), rendered);
    }

    @Override
    public boolean hasPermission(String permission) {
        // Console has unrestricted access by design.
        return true;
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

    private void logWithPlatformLogger(PlatformLogger platformLogger, LogLevel level, String message) {
        if (platformLogger == null) {
            return;
        }
        if (level == null) {
            platformLogger.info(message);
            return;
        }
        switch (level) {
            case WARN -> platformLogger.warn(message);
            case ERROR -> platformLogger.error(message);
            case DEBUG -> platformLogger.debug(message);
            case TRACE -> platformLogger.debug(message);
            case SUCCESS, INFO -> platformLogger.info(message);
        }
    }

}
