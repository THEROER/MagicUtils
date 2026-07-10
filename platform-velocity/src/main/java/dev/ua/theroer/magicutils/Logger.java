package dev.ua.theroer.magicutils;

import com.velocitypowered.api.proxy.Player;
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
import dev.ua.theroer.magicutils.platform.velocity.VelocityAudience;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Velocity logger adapter backed by {@link LoggerCore}.
 *
 * <p>Gives Velocity the same typed logger facade as Bukkit/Fabric/NeoForge:
 * generated {@code info/warn/error/success/debug/trace} methods, player-typed
 * overloads keyed on {@link Player}, prefixed sub-loggers, and a fluent
 * {@link LogBuilder}. Console routing reuses the platform's structured console
 * audience.
 */
@LogMethods(staticMethods = false, audienceType = "com.velocitypowered.api.proxy.Player")
public final class Logger extends LoggerMethods implements LoggerAdapter<Player, PrefixedLogger> {
    private final LoggerCore core;
    private final Map<String, PrefixedLogger> prefixedLoggers = new HashMap<>();

    /**
     * Creates a Velocity logger instance.
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
    protected void send(LogLevel level, Object message, Player player) {
        send(level, message, player, null, LogTarget.CHAT, false);
    }

    @Override
    protected void send(LogLevel level, Object message, Player player, boolean all) {
        send(level, message, player, null, getDefaultTarget(), all);
    }

    @Override
    protected void sendToConsole(LogLevel level, Object message) {
        send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    @Override
    protected void sendToPlayers(LogLevel level, Object message, Collection<? extends Player> players) {
        send(level, message, null, players, LogTarget.CHAT, false);
    }

    @Override
    public Audience wrapAudience(Player player) {
        return player != null ? new VelocityAudience(player) : null;
    }
}
