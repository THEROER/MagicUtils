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
import dev.ua.theroer.magicutils.platform.fabric.FabricAudience;
import dev.ua.theroer.magicutils.platform.fabric.FabricCommandAudience;
import dev.ua.theroer.magicutils.platform.fabric.FabricComponentSerializer;
import dev.ua.theroer.magicutils.platform.fabric.FabricExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.platform.fabric.FabricPlaceholderRegistrar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Fabric logger adapter backed by {@link LoggerCore}.
 */
@LogMethods(staticMethods = false, audienceType = "net.minecraft.server.network.ServerPlayerEntity")
public final class Logger extends LoggerMethods {
    private final LoggerCore core;
    private final Map<String, PrefixedLogger> prefixedLoggers = new HashMap<>();

    public Logger(Platform platform, ConfigManager manager, Object placeholderOwner, String modName) {
        this.core = new LoggerCore(platform, manager, placeholderOwner, modName);
        this.core.setExternalPlaceholderEngine(new FabricExternalPlaceholderEngine(core));
        FabricPlaceholderRegistrar.install(core);
        applyTextMaxLength(core.getConfig());
        if (manager != null) {
            manager.onChange(LoggerConfig.class, (cfg, sections) -> applyTextMaxLength(cfg));
        }
    }

    public Logger(Platform platform, ConfigManager manager, String modName) {
        this(platform, manager, null, modName);
    }

    public Logger(Platform platform, ConfigManager manager) {
        this(platform, manager, null, null);
    }

    public LoggerCore getCore() {
        return core;
    }

    public LoggerConfig getConfig() {
        return core.getConfig();
    }

    private void applyTextMaxLength(LoggerConfig config) {
        if (config == null || config.getDefaults() == null) {
            return;
        }
        FabricComponentSerializer.setMaxJsonLength(config.getDefaults().getTextMaxLength());
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
                                  @Nullable ServerPlayerEntity player,
                                  @Nullable Collection<? extends ServerPlayerEntity> players,
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
                     @Nullable ServerPlayerEntity player,
                     @Nullable Collection<? extends ServerPlayerEntity> players,
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
    protected void send(LogLevel level, Object message, ServerPlayerEntity player) {
        send(level, message, player, null, LogTarget.CHAT, false);
    }

    @Override
    protected void send(LogLevel level, Object message, ServerPlayerEntity player, boolean all) {
        send(level, message, player, null, getDefaultTarget(), all);
    }

    @Override
    protected void sendToConsole(LogLevel level, Object message) {
        send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    @Override
    protected void sendToPlayers(LogLevel level, Object message, Collection<? extends ServerPlayerEntity> players) {
        send(level, message, null, players, LogTarget.CHAT, false);
    }

    public Audience wrapAudience(ServerPlayerEntity player) {
        return player != null ? new FabricAudience(player) : null;
    }

    public Audience wrapAudience(ServerCommandSource source) {
        return source != null ? new FabricCommandAudience(source, false) : null;
    }

    public Audience wrapAudience(ServerCommandSource source, boolean broadcastToOps) {
        return source != null ? new FabricCommandAudience(source, broadcastToOps) : null;
    }

    public Audience wrapErrorAudience(ServerCommandSource source) {
        return source != null ? new FabricCommandAudience(source, false, FabricCommandAudience.Mode.ERROR) : null;
    }

    private Collection<? extends Audience> wrapAudiences(Collection<? extends ServerPlayerEntity> players) {
        if (players == null || players.isEmpty()) {
            return null;
        }
        return players.stream()
                .filter(player -> player != null)
                .map(this::wrapAudience)
                .toList();
    }
}
