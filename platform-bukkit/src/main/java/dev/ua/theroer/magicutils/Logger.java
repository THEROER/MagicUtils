package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.logger.ExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.logger.LogBuilder;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogMethods;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.logger.PrefixMode;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitAudienceWrapper;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitPlaceholderRegistrar;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Bukkit/Paper logger adapter backed by {@link LoggerCore}.
 */
@LogMethods(staticMethods = false, audienceType = "org.bukkit.entity.Player")
public final class Logger extends LoggerMethods {
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

    public LoggerConfig getConfig() {
        return core.getConfig();
    }

    public MiniMessage getMiniMessage() {
        return core.getMiniMessage();
    }

    public Object getPlaceholderOwner() {
        return core.getPlaceholderOwner();
    }

    public ExternalPlaceholderEngine getExternalPlaceholderEngine() {
        return core.getExternalPlaceholderEngine();
    }

    public void setExternalPlaceholderEngine(ExternalPlaceholderEngine engine) {
        core.setExternalPlaceholderEngine(engine);
    }

    public LanguageManager getLanguageManager() {
        return core.getLanguageManager();
    }

    public void setLanguageManager(LanguageManager languageManager) {
        core.setLanguageManager(languageManager);
    }

    public void setAutoLocalization(boolean enabled) {
        core.setAutoLocalization(enabled);
    }

    public void reload() {
        core.reload();
    }

    public PrefixMode getChatPrefixMode() {
        return core.getChatPrefixMode();
    }

    public void setChatPrefixMode(PrefixMode mode) {
        core.setChatPrefixMode(mode);
    }

    public PrefixMode getConsolePrefixMode() {
        return core.getConsolePrefixMode();
    }

    public void setConsolePrefixMode(PrefixMode mode) {
        core.setConsolePrefixMode(mode);
    }

    public String getCustomPrefix() {
        return core.getCustomPrefix();
    }

    public void setCustomPrefix(String customPrefix) {
        core.setCustomPrefix(customPrefix);
    }

    public LogTarget getDefaultTarget() {
        return core.getDefaultTarget();
    }

    public void setDefaultTarget(LogTarget target) {
        core.setDefaultTarget(target);
    }

    public boolean isConsoleStripFormatting() {
        return core.isConsoleStripFormatting();
    }

    public void setConsoleStripFormatting(boolean consoleStripFormatting) {
        core.setConsoleStripFormatting(consoleStripFormatting);
    }

    public boolean isConsoleUseGradient() {
        return core.isConsoleUseGradient();
    }

    public void setConsoleUseGradient(boolean consoleUseGradient) {
        core.setConsoleUseGradient(consoleUseGradient);
    }

    public String[] resolveColorsForLevel(LogLevel level, boolean forConsole) {
        return core.resolveColorsForLevel(level, forConsole);
    }

    public Component parseMessage(Object message,
                                  LogLevel level,
                                  LogTarget target,
                                  @Nullable Player player,
                                  @Nullable Collection<? extends Player> players,
                                  Object... placeholdersArgs) {
        return core.parseMessage(message, level, target, wrapAudience(player), wrapAudiences(players), placeholdersArgs);
    }

    public Component parseSmart(String input) {
        return MessageParser.parseSmart(input);
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

    public PrefixedLogger create(String name) {
        return withPrefix(name);
    }

    public PrefixedLogger create(String name, String prefix) {
        return withPrefix(name, prefix);
    }

    public PrefixedLogger withPrefix(String name) {
        return withPrefix(name, "[" + name + "]");
    }

    public PrefixedLogger withPrefix(String name, String prefix) {
        return prefixedLoggers.computeIfAbsent(name, key -> {
            PrefixedLoggerCore prefixedCore = core.withPrefix(name, prefix);
            return new PrefixedLogger(this, prefixedCore);
        });
    }

    public void broadcast(Object message) {
        send(LogLevel.INFO, message, null, null, LogTarget.BOTH, true);
    }

    public void setPrefixedLoggerEnabled(String name, boolean enabled) {
        core.setPrefixedLoggerEnabled(name, enabled);
    }

    public void send(LogLevel level,
                     Object message,
                     @Nullable Player player,
                     @Nullable Collection<? extends Player> players,
                     LogTarget target,
                     boolean broadcast,
                     Object... placeholders) {
        core.send(level, message, wrapAudience(player), wrapAudiences(players), target, broadcast, placeholders);
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

    private Collection<? extends Audience> wrapAudiences(Collection<? extends CommandSender> senders) {
        if (senders == null || senders.isEmpty()) {
            return null;
        }
        return senders.stream()
                .filter(sender -> sender != null)
                .map(BukkitAudienceWrapper::new)
                .toList();
    }
}
