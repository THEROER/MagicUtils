package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.logger.ComponentPrefixStripper;
import dev.ua.theroer.magicutils.logger.ConsoleMessageParser;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Console audience that routes messages through a JUL logger with proper levels.
 */
public final class BukkitConsoleAudience implements Audience {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final ComponentLoggerSupport COMPONENT_LOGGER = ComponentLoggerSupport.load();

    private final Logger baseLogger;
    private final String baseLoggerName;
    private final Map<String, Logger> loggerCache = new ConcurrentHashMap<>();
    private final Map<String, Object> componentLoggerCache = new ConcurrentHashMap<>();

    public BukkitConsoleAudience(Logger baseLogger, String baseLoggerName) {
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
        String plain = PLAIN.serialize(component);
        ConsoleMessageParser.ParsedMessage parsed = ConsoleMessageParser.parse(plain);
        Component stripped = ComponentPrefixStripper.stripPrefix(component, parsed.prefixText());
        String loggerName = buildLoggerName(baseLoggerName, parsed.subLogger());
        if (logWithComponentLogger(loggerName, parsed.level(), stripped)) {
            return;
        }
        Logger logger = resolveLogger(loggerName);
        logWithLevel(logger, parsed.level(), parsed.message());
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
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(true);
        if (baseLogger != null && logger.getParent() == null) {
            Logger parent = baseLogger.getParent();
            logger.setParent(parent != null ? parent : baseLogger);
        }
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
        Level jul = toJulLevel(level);
        if (!logger.isLoggable(jul)) {
            return;
        }
        logger.log(jul, message);
    }

    private Level toJulLevel(LogLevel level) {
        if (level == null) {
            return Level.INFO;
        }
        return switch (level) {
            case WARN -> Level.WARNING;
            case ERROR -> Level.SEVERE;
            case DEBUG -> Level.FINE;
            case TRACE -> Level.FINER;
            case SUCCESS, INFO -> Level.INFO;
        };
    }

    private boolean logWithComponentLogger(String loggerName, LogLevel level, Component message) {
        if (COMPONENT_LOGGER == null || loggerName == null || loggerName.isBlank()) {
            return false;
        }
        Object componentLogger = componentLoggerCache.computeIfAbsent(loggerName, COMPONENT_LOGGER::createLogger);
        if (componentLogger == null) {
            return false;
        }
        return COMPONENT_LOGGER.log(componentLogger, level, message);
    }

    private static final class ComponentLoggerSupport {
        private final Method factory;
        private final Method info;
        private final Method warn;
        private final Method error;
        private final Method debug;
        private final Method trace;
        private final Method isDebugEnabled;
        private final Method isTraceEnabled;

        private ComponentLoggerSupport(Method factory,
                                       Method info,
                                       Method warn,
                                       Method error,
                                       Method debug,
                                       Method trace,
                                       Method isDebugEnabled,
                                       Method isTraceEnabled) {
            this.factory = factory;
            this.info = info;
            this.warn = warn;
            this.error = error;
            this.debug = debug;
            this.trace = trace;
            this.isDebugEnabled = isDebugEnabled;
            this.isTraceEnabled = isTraceEnabled;
        }

        static ComponentLoggerSupport load() {
            try {
                Class<?> loggerClass = Class.forName("net.kyori.adventure.text.logger.slf4j.ComponentLogger");
                Method factory = loggerClass.getMethod("logger", String.class);
                Method info = loggerClass.getMethod("info", Component.class);
                Method warn = loggerClass.getMethod("warn", Component.class);
                Method error = loggerClass.getMethod("error", Component.class);
                Method debug = loggerClass.getMethod("debug", Component.class);
                Method trace = loggerClass.getMethod("trace", Component.class);
                Method isDebugEnabled = loggerClass.getMethod("isDebugEnabled");
                Method isTraceEnabled = loggerClass.getMethod("isTraceEnabled");
                return new ComponentLoggerSupport(factory, info, warn, error, debug, trace, isDebugEnabled, isTraceEnabled);
            } catch (Throwable ignored) {
                return null;
            }
        }

        Object createLogger(String name) {
            try {
                return factory.invoke(null, name);
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean log(Object logger, LogLevel level, Component message) {
            if (logger == null || message == null) {
                return false;
            }
            try {
                if (level == null) {
                    info.invoke(logger, message);
                    return true;
                }
                switch (level) {
                    case WARN -> warn.invoke(logger, message);
                    case ERROR -> error.invoke(logger, message);
                    case DEBUG -> {
                        if (isEnabled(isDebugEnabled, logger)) {
                            debug.invoke(logger, message);
                        }
                    }
                    case TRACE -> {
                        if (isEnabled(isTraceEnabled, logger)) {
                            trace.invoke(logger, message);
                        } else if (isEnabled(isDebugEnabled, logger)) {
                            debug.invoke(logger, message);
                        }
                    }
                    case SUCCESS, INFO -> info.invoke(logger, message);
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean isEnabled(Method method, Object logger) {
            try {
                Object result = method.invoke(logger);
                return result instanceof Boolean && (Boolean) result;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
