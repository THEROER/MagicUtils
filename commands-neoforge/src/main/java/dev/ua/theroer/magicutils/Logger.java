package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import dev.ua.theroer.magicutils.logger.LogBuilder;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogMethods;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerAdapter;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.ConfigNamespaceProvider;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleListener;
import dev.ua.theroer.magicutils.platform.PlayerLocaleListener;
import dev.ua.theroer.magicutils.platform.PlayerMessageListener;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import dev.ua.theroer.magicutils.platform.neoforge.NeoForgeCommandAudience;
import dev.ua.theroer.magicutils.platform.neoforge.NeoForgePlayerAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * NeoForge logger adapter backed by {@link LoggerCore}.
 */
@LogMethods(staticMethods = false, audienceType = "net.minecraft.server.level.ServerPlayer")
public final class Logger extends LoggerMethods implements LoggerAdapter<ServerPlayer, PrefixedLogger> {
    private final LoggerCore core;
    private final Map<String, PrefixedLogger> prefixedLoggers = new HashMap<>();

    /**
     * Creates a NeoForge logger instance.
     *
     * @param platform platform adapter
     * @param manager config manager
     * @param placeholderOwner placeholder owner key
     * @param modName mod name for logger prefix
     */
    public Logger(Platform platform, ConfigManager manager, Object placeholderOwner, String modName) {
        Platform effectivePlatform = wrapPlatform(platform, modName);
        this.core = new LoggerCore(effectivePlatform, manager, placeholderOwner, modName);
    }

    /**
     * Creates a NeoForge logger instance.
     *
     * @param platform platform adapter
     * @param manager config manager
     * @param modName mod name for logger prefix
     */
    public Logger(Platform platform, ConfigManager manager, String modName) {
        this(platform, manager, null, modName);
    }

    /**
     * Creates a NeoForge logger instance with default mod name.
     *
     * @param platform platform adapter
     * @param manager config manager
     */
    public Logger(Platform platform, ConfigManager manager) {
        this(platform, manager, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoggerCore getCore() {
        return core;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, PrefixedLogger> getPrefixedLoggers() {
        return prefixedLoggers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrefixedLogger buildPrefixedLogger(PrefixedLoggerCore core) {
        return new PrefixedLogger(this, core);
    }

    /**
     * Creates a log builder with INFO level.
     *
     * @return log builder
     */
    public LogBuilder log() {
        return new LogBuilder(this, LogLevel.INFO);
    }

    /**
     * Creates a log builder without mod prefix.
     *
     * @return log builder
     */
    public LogBuilder noPrefix() {
        return new LogBuilder(this, LogLevel.INFO).noPrefix();
    }

    /**
     * Creates a log builder with INFO level.
     *
     * @return log builder
     */
    public LogBuilder info() {
        return new LogBuilder(this, LogLevel.INFO);
    }

    /**
     * Creates a log builder with WARN level.
     *
     * @return log builder
     */
    public LogBuilder warn() {
        return new LogBuilder(this, LogLevel.WARN);
    }

    /**
     * Creates a log builder with ERROR level.
     *
     * @return log builder
     */
    public LogBuilder error() {
        return new LogBuilder(this, LogLevel.ERROR);
    }

    /**
     * Creates a log builder with DEBUG level.
     *
     * @return log builder
     */
    public LogBuilder debug() {
        return new LogBuilder(this, LogLevel.DEBUG);
    }

    /**
     * Creates a log builder with SUCCESS level.
     *
     * @return log builder
     */
    public LogBuilder success() {
        return new LogBuilder(this, LogLevel.SUCCESS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(LogLevel level, Object message) {
        send(level, message, null, null, getDefaultTarget(), false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(LogLevel level, Object message, ServerPlayer player) {
        send(level, message, player, null, LogTarget.CHAT, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void send(LogLevel level, Object message, ServerPlayer player, boolean all) {
        send(level, message, player, null, getDefaultTarget(), all);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void sendToConsole(LogLevel level, Object message) {
        send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void sendToPlayers(LogLevel level, Object message, Collection<? extends ServerPlayer> players) {
        send(level, message, null, players, LogTarget.CHAT, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Audience wrapAudience(ServerPlayer player) {
        return player != null ? new NeoForgePlayerAudience(player) : null;
    }

    /**
     * Wraps a command source as an audience.
     *
     * @param source command source
     * @return wrapped audience or null
     */
    public Audience wrapAudience(CommandSourceStack source) {
        return source != null ? new NeoForgeCommandAudience(source, false) : null;
    }

    /**
     * Wraps a command source as an audience with optional op broadcast.
     *
     * @param source command source
     * @param broadcastToOps whether to broadcast to ops
     * @return wrapped audience or null
     */
    public Audience wrapAudience(CommandSourceStack source, boolean broadcastToOps) {
        return source != null ? new NeoForgeCommandAudience(source, broadcastToOps) : null;
    }

    /**
     * Wraps a command source as an error audience.
     *
     * @param source command source
     * @return wrapped audience or null
     */
    public Audience wrapErrorAudience(CommandSourceStack source) {
        return source != null ? new NeoForgeCommandAudience(source, false, NeoForgeCommandAudience.Mode.ERROR) : null;
    }

    private static Platform wrapPlatform(Platform platform, String modName) {
        if (platform == null) {
            return null;
        }
        String baseLoggerName = resolveBaseLoggerName(modName);
        Audience console = new NeoForgeConsoleAudienceLogger(platform.logger(), baseLoggerName);
        return new ConsoleOverridePlatform(platform, console);
    }

    private static String resolveBaseLoggerName(String modName) {
        if (modName != null && !modName.isBlank()) {
            return modName.trim();
        }
        return "MagicUtils-NeoForge";
    }

    /**
     * Console audience that routes messages through SLF4J with proper log levels.
     */
    private static final class NeoForgeConsoleAudienceLogger implements StructuredConsoleAudience {
        private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

        private final PlatformLogger logger;
        private final String baseLoggerName;

        private NeoForgeConsoleAudienceLogger(PlatformLogger logger, String baseLoggerName) {
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
                    logWithPlatformLogger(logger, metadata.level(), PLAIN.serialize(component));
                }
                return;
            }
            String loggerName = buildLoggerName(baseLoggerName, metadata.subLoggerName());
            org.slf4j.Logger slf4j = LoggerFactory.getLogger(loggerName);
            logWithLevel(slf4j, metadata.level(), PLAIN.serialize(component));
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        private String buildLoggerName(String baseName, String subLogger) {
            if (subLogger == null || subLogger.isBlank()) {
                return baseName;
            }
            return baseName + "-" + subLogger.trim().replaceAll("\\s+", "-");
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
                case DEBUG, TRACE -> platformLogger.debug(message);
                case SUCCESS, INFO -> platformLogger.info(message);
            }
        }

        private void logWithLevel(org.slf4j.Logger slf4j, LogLevel level, String message) {
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

    private static final class ConsoleOverridePlatform implements Platform, ConfigNamespaceProvider, ConfigFormatProvider {
        private final Platform delegate;
        private final Audience console;

        private ConsoleOverridePlatform(Platform delegate, Audience console) {
            this.delegate = delegate;
            this.console = console;
        }

        @Override
        public Path configDir() {
            return delegate.configDir();
        }

        @Override
        public PlatformLogger logger() {
            return delegate.logger();
        }

        @Override
        public Audience console() {
            return console;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return delegate.onlinePlayers();
        }

        @Override
        public void runOnMain(Runnable task) {
            delegate.runOnMain(task);
        }

        @Override
        public boolean isMainThread() {
            return delegate.isMainThread();
        }

        @Override
        public ThreadContext threadContext() {
            return delegate.threadContext();
        }

        @Override
        public TaskScheduler scheduler() {
            return delegate.scheduler();
        }

        @Override
        public ListenerSubscription subscribePlayerMessages(PlayerMessageListener listener) {
            return delegate.subscribePlayerMessages(listener);
        }

        @Override
        public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
            return delegate.subscribePlayerLifecycle(listener);
        }

        @Override
        public ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
            return delegate.subscribePlayerLocales(listener);
        }

        @Override
        public String resolveConfigNamespace(String pluginName) {
            if (delegate instanceof ConfigNamespaceProvider provider) {
                return provider.resolveConfigNamespace(pluginName);
            }
            return null;
        }

        @Override
        public String defaultConfigExtension() {
            if (delegate instanceof ConfigFormatProvider provider) {
                return provider.defaultConfigExtension();
            }
            return null;
        }
    }
}
