package dev.ua.theroer.magicutils.platform.velocity;

import dev.ua.theroer.magicutils.logger.ConsoleColorSerializer;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Console audience that routes messages through an SLF4J logger with proper levels.
 */
final class VelocityConsoleAudience implements StructuredConsoleAudience {

    private final Logger baseLogger;
    private final String baseLoggerName;
    private final Map<String, Logger> loggerCache = new ConcurrentHashMap<>();

    VelocityConsoleAudience(Logger baseLogger) {
        this.baseLogger = baseLogger;
        this.baseLoggerName = baseLogger != null ? baseLogger.getName() : null;
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
        String loggerName = buildLoggerName(baseLoggerName, metadata.subLoggerName());
        Logger logger = resolveLogger(loggerName);
        logWithLevel(logger, metadata.level(), ConsoleColorSerializer.serialize(component));
    }

    @Override
    public boolean hasPermission(String permission) {
        // Console has unrestricted access by design.
        return true;
    }

    private Logger resolveLogger(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return baseLogger != null ? baseLogger : LoggerFactory.getLogger("MagicUtils-Velocity");
        }
        return loggerCache.computeIfAbsent(loggerName, LoggerFactory::getLogger);
    }

    private String normalizeSegment(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.replaceAll("\\s+", "-");
    }

    private String buildLoggerName(String baseName, String subLogger) {
        if (subLogger == null || subLogger.isBlank()) {
            return baseName;
        }
        String normalized = normalizeSegment(subLogger);
        return baseName != null ? baseName + "-" + normalized : normalized;
    }

    private void logWithLevel(Logger logger, LogLevel level, String message) {
        if (logger == null) {
            return;
        }
        if (level == null) {
            logger.info(message);
            return;
        }
        switch (level) {
            case WARN -> logger.warn(message);
            case ERROR -> logger.error(message);
            case DEBUG -> logger.debug(message);
            case TRACE -> logger.trace(message);
            case SUCCESS, INFO -> logger.info(message);
        }
    }
}
