package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
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
import dev.ua.theroer.magicutils.platform.fabric.FabricAudience;
import dev.ua.theroer.magicutils.platform.fabric.FabricCommandAudience;
import dev.ua.theroer.magicutils.platform.fabric.FabricComponentSerializer;
import dev.ua.theroer.magicutils.platform.fabric.FabricExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.platform.fabric.FabricPlaceholderRegistrar;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Fabric logger adapter backed by {@link LoggerCore}.
 */
@LogMethods(staticMethods = false, audienceType = "net.minecraft.server.network.ServerPlayerEntity")
public final class Logger extends LoggerMethods implements LoggerAdapter<ServerPlayerEntity, PrefixedLogger> {
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

    @Override
    public LoggerCore getCore() {
        return core;
    }

    @Override
    public Map<String, PrefixedLogger> getPrefixedLoggers() {
        return prefixedLoggers;
    }

    @Override
    public PrefixedLogger buildPrefixedLogger(PrefixedLoggerCore core) {
        return new PrefixedLogger(this, core);
    }

    private void applyTextMaxLength(LoggerConfig config) {
        if (config == null || config.getDefaults() == null) {
            return;
        }
        FabricComponentSerializer.setMaxJsonLength(config.getDefaults().getTextMaxLength());
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
    protected void sendToPlayers(LogLevel level, Object message, java.util.Collection<? extends ServerPlayerEntity> players) {
        send(level, message, null, players, LogTarget.CHAT, false);
    }

    @Override
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
}
