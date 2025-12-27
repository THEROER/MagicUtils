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
import dev.ua.theroer.magicutils.platform.bukkit.BukkitAudienceWrapper;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitPlaceholderRegistrar;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Bukkit/Paper logger adapter backed by {@link LoggerCore}.
 */
@LogMethods(staticMethods = false, audienceType = "org.bukkit.entity.Player")
public final class Logger extends LoggerMethods implements LoggerAdapter<Player, PrefixedLogger> {
    @Getter
    private final LoggerCore core;
    @Getter
    private final JavaPlugin plugin;
    private final Map<String, PrefixedLogger> prefixedLoggers = new HashMap<>();

    /**
     * Create a new logger bound to the provided platform and config manager.
     *
     * @param platform platform adapter
     * @param plugin owning plugin instance
     * @param manager config manager
     */
    public Logger(Platform platform, JavaPlugin plugin, ConfigManager manager) {
        this.core = new LoggerCore(platform, manager, plugin, plugin != null ? plugin.getName() : null);
        this.plugin = plugin;
        this.core.setExternalPlaceholderEngine(new BukkitExternalPlaceholderEngine(plugin));
        BukkitPlaceholderRegistrar.install(plugin);
    }

    @Override
    public Map<String, PrefixedLogger> getPrefixedLoggers() {
        return prefixedLoggers;
    }

    @Override
    public PrefixedLogger buildPrefixedLogger(PrefixedLoggerCore core) {
        return new PrefixedLogger(this, core);
    }

    @Override
    public Audience wrapAudience(Player player) {
        return wrapAudience((CommandSender) player);
    }

    public LogBuilder log() {
        return new LogBuilder(this, LogLevel.INFO);
    }

    public LogBuilder noPrefix() {
        return new LogBuilder(this, LogLevel.INFO).noPrefix();
    }

    public LogBuilder info() {
        return new LogBuilder(this, LogLevel.INFO);
    }

    public LogBuilder warn() {
        return new LogBuilder(this, LogLevel.WARN);
    }

    public LogBuilder error() {
        return new LogBuilder(this, LogLevel.ERROR);
    }

    public LogBuilder debug() {
        return new LogBuilder(this, LogLevel.DEBUG);
    }

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

    public Audience wrapAudience(CommandSender sender) {
        return sender != null ? new BukkitAudienceWrapper(sender) : null;
    }
}
