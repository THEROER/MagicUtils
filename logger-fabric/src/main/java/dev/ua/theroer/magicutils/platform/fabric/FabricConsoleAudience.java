package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.ConsoleColorSerializer;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;

/**
 * Console audience that logs through PlatformLogger.
 */
public final class FabricConsoleAudience implements StructuredConsoleAudience {
    private static final ANSIComponentSerializer ANSI = ANSIComponentSerializer.ansi();

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
        sendConsole(component, new ConsoleMessageMetadata(LogLevel.INFO, null));
    }

    @Override
    public void sendConsole(Component component, ConsoleMessageMetadata metadata) {
        if (component == null || metadata == null) {
            return;
        }
        if (baseLoggerName == null) {
            if (logger != null) {
                logWithPlatformLogger(logger, metadata.level(), ConsoleColorSerializer.serialize(component));
            }
            return;
        }
        String loggerName = buildLoggerName(baseLoggerName, metadata.subLoggerName());
        Logger slf4j = LoggerFactory.getLogger(loggerName);
        String rendered = ANSI.serialize(component);
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
