package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.Logger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * NeoForge logger instance with custom prefix.
 */
@LogMethods(staticMethods = false, audienceType = "net.minecraft.server.level.ServerPlayer")
public class PrefixedLogger extends PrefixedLoggerMethods implements PrefixedLoggerAdapter<ServerPlayer, LogBuilder> {
    private final Logger logger;
    private final PrefixedLoggerCore core;

    /**
     * Creates a new NeoForge prefixed logger.
     *
     * @param logger base logger adapter
     * @param core prefixed logger core
     */
    public PrefixedLogger(Logger logger, PrefixedLoggerCore core) {
        this.logger = logger;
        this.core = core;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrefixedLoggerCore getCore() {
        return core;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoggerAdapter<ServerPlayer, ?> getLoggerAdapter() {
        return logger;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogBuilder buildLogBuilder(LogLevel level) {
        return new PrefixedLogBuilder(level);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(LogLevel level, Object message) {
        PrefixedLoggerAdapter.super.send(level, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(LogLevel level, Object message, ServerPlayer player) {
        PrefixedLoggerAdapter.super.send(level, message, player);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(LogLevel level, Object message, ServerPlayer player, boolean all) {
        PrefixedLoggerAdapter.super.send(level, message, player, all);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendToConsole(LogLevel level, Object message) {
        PrefixedLoggerAdapter.super.sendToConsole(level, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendToPlayers(LogLevel level, Object message, Collection<? extends ServerPlayer> players) {
        PrefixedLoggerAdapter.super.sendToPlayers(level, message, players);
    }

    private class PrefixedLogBuilder extends LogBuilder {
        PrefixedLogBuilder(LogLevel level) {
            super(logger, level);
        }

        @Override
        protected void performSend(Object message, Object... placeholders) {
            if (!core.isEnabled()) {
                return;
            }
            LogTarget finalTarget = getTarget() != null ? getTarget() : logger.getCore().getDefaultTarget();
            Collection<? extends dev.ua.theroer.magicutils.platform.Audience> audienceRecipients = null;
            if (!getRecipients().isEmpty()) {
                audienceRecipients = getRecipients();
            }
            logger.getCore().send(
                    level,
                    message,
                    getAudience(),
                    audienceRecipients,
                    finalTarget,
                    isBroadcast(),
                    new ConsoleMessageMetadata(level, core.getName()),
                    core.getPrefix(),
                    getPrefixOverride(),
                    placeholders
            );
        }
    }
}
