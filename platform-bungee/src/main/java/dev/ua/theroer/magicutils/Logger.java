package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.LogBuilder;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogMethods;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerAdapter;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.bungee.BungeeAudience;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.md_5.bungee.api.connection.ProxiedPlayer;

/**
 * BungeeCord logger: the single output surface for a plugin.
 *
 * <p>Despite the name, this is not only for console logs. The same level methods
 * send to the console (a log line) and to players (a chat message), so one call
 * site covers both. Text is authored once with MiniMessage or legacy {@code &}
 * codes and rendered correctly on every platform.
 *
 * <p><b>Everyday use</b> is the short level methods; reach for the fluent
 * {@link #log()} builder only when you need several recipients, tag resolvers,
 * or a per-message prefix override:
 *
 * <pre>{@code
 * // Console log line:
 * logger.info("<green>Ready</green>, <yellow>%d</yellow> loaded", count);
 *
 * // Message a player (same levels, different target):
 * logger.info(player, "<green>Connected");
 *
 * // Composite case -> builder:
 * logger.warn().to(player).toConsole().send("<red>Backend down");
 * }</pre>
 *
 * <p>Backed by {@link LoggerCore}; obtain a prefixed sub-logger with
 * {@link #create(String)}. Console routing reuses the platform's structured
 * console audience.
 *
 * @see LoggerAdapter
 */
@LogMethods(staticMethods = false, audienceType = "net.md_5.bungee.api.connection.ProxiedPlayer")
public final class Logger extends LoggerMethods implements LoggerAdapter<ProxiedPlayer, PrefixedLogger> {
    private final LoggerCore core;
    private final Map<String, PrefixedLogger> prefixedLoggers = new HashMap<>();

    /**
     * Creates a BungeeCord logger instance.
     *
     * @param platform platform adapter
     * @param manager config manager
     * @param placeholderOwner placeholder owner key
     * @param pluginName plugin name for the logger prefix
     */
    public Logger(Platform platform, ConfigManager manager, Object placeholderOwner, String pluginName) {
        this.core = new LoggerCore(platform, manager, placeholderOwner, pluginName);
    }

    @Override
    public LoggerCore getCore() {
        return core;
    }

    @Override
    public Map<String, PrefixedLogger> getPrefixedLoggers() {
        return prefixedLoggers;
    }

    @Override
    public PrefixedLogger buildPrefixedLogger(PrefixedLoggerCore prefixedCore) {
        return new PrefixedLogger(this, prefixedCore);
    }

    /**
     * Creates an INFO level log builder.
     *
     * @return log builder
     */
    public LogBuilder log() {
        return new LogBuilder(this, LogLevel.INFO);
    }

    /**
     * Creates an INFO level log builder with the prefix disabled.
     *
     * @return log builder
     */
    public LogBuilder noPrefix() {
        return new LogBuilder(this, LogLevel.INFO).noPrefix();
    }

    /**
     * Creates an INFO level log builder.
     *
     * @return log builder
     */
    public LogBuilder info() {
        return new LogBuilder(this, LogLevel.INFO);
    }

    /**
     * Creates a WARN level log builder.
     *
     * @return log builder
     */
    public LogBuilder warn() {
        return new LogBuilder(this, LogLevel.WARN);
    }

    /**
     * Creates an ERROR level log builder.
     *
     * @return log builder
     */
    public LogBuilder error() {
        return new LogBuilder(this, LogLevel.ERROR);
    }

    /**
     * Creates a DEBUG level log builder.
     *
     * @return log builder
     */
    public LogBuilder debug() {
        return new LogBuilder(this, LogLevel.DEBUG);
    }

    /**
     * Creates a SUCCESS level log builder.
     *
     * @return log builder
     */
    public LogBuilder success() {
        return new LogBuilder(this, LogLevel.SUCCESS);
    }

    @Override
    protected void send(LogLevel level, Object message) {
        send(level, message, null, null, getDefaultTarget(), false);
    }

    @Override
    protected void send(LogLevel level, Object message, ProxiedPlayer player) {
        send(level, message, player, null, LogTarget.CHAT, false);
    }

    @Override
    protected void send(LogLevel level, Object message, ProxiedPlayer player, boolean all) {
        send(level, message, player, null, getDefaultTarget(), all);
    }

    @Override
    protected void sendToConsole(LogLevel level, Object message) {
        send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    @Override
    protected void sendToPlayers(LogLevel level, Object message, Collection<? extends ProxiedPlayer> players) {
        send(level, message, null, players, LogTarget.CHAT, false);
    }

    @Override
    public Audience wrapAudience(ProxiedPlayer player) {
        return player != null ? new BungeeAudience(player) : null;
    }
}
