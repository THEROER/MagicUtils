package dev.ua.theroer.magicutils.platform.velocity;

import dev.ua.theroer.magicutils.logger.ConsoleMessageParser;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Console audience that routes messages through an SLF4J logger with proper levels.
 */
final class VelocityConsoleAudience implements Audience {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
        String plain = PLAIN.serialize(component);
        ConsoleMessageParser.ParsedMessage parsed = ConsoleMessageParser.parse(plain);
        String loggerName = buildLoggerName(baseLoggerName, parsed.subLogger());
        Logger logger = resolveLogger(loggerName);
        logWithLevel(logger, parsed.level(), parsed.message());
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
