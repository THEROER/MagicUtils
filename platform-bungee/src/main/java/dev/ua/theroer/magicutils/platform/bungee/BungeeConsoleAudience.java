package dev.ua.theroer.magicutils.platform.bungee;

import dev.ua.theroer.magicutils.logger.ConsoleColorSerializer;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JUL-backed console audience for BungeeCord.
 */
final class BungeeConsoleAudience implements StructuredConsoleAudience {

    private final Logger baseLogger;
    private final String baseLoggerName;
    private final Map<String, Logger> loggerCache = new ConcurrentHashMap<>();

    BungeeConsoleAudience(Logger baseLogger, String baseLoggerName) {
        this.baseLogger = baseLogger;
        this.baseLoggerName = baseLoggerName != null && !baseLoggerName.isBlank()
                ? baseLoggerName
                : baseLogger != null ? baseLogger.getName() : null;
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
        logWithLevel(resolveLogger(loggerName), metadata.level(), ConsoleColorSerializer.serialize(component));
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    private Logger resolveLogger(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) {
            return baseLogger;
        }
        Logger logger = loggerCache.computeIfAbsent(loggerName, Logger::getLogger);
        configureLogger(logger);
        return logger;
    }

    private void configureLogger(Logger logger) {
        if (logger == null) {
            return;
        }
        if (logger.getLevel() == null && baseLogger != null && baseLogger.getLevel() != null) {
            logger.setLevel(baseLogger.getLevel());
        }
        logger.setUseParentHandlers(true);
        if (baseLogger != null && logger.getParent() == null) {
            Logger parent = baseLogger.getParent();
            logger.setParent(parent != null ? parent : baseLogger);
        }
    }

    private String buildLoggerName(String baseName, String subLogger) {
        if (subLogger == null || subLogger.isBlank()) {
            return baseName;
        }
        String normalized = subLogger.trim().replaceAll("\\s+", "-");
        return baseName != null ? baseName + "-" + normalized : normalized;
    }

    private void logWithLevel(Logger logger, LogLevel level, String message) {
        if (logger == null) {
            return;
        }
        Level target = switch (level != null ? level : LogLevel.INFO) {
            case WARN -> Level.WARNING;
            case ERROR -> Level.SEVERE;
            case DEBUG -> Level.FINE;
            case TRACE -> Level.FINER;
            case SUCCESS, INFO -> Level.INFO;
        };
        if (!logger.isLoggable(target)) {
            return;
        }
        logger.log(target, message);
    }
}
