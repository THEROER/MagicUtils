package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * Shared adapter for prefixed logger wrappers.
 *
 * @param <P> platform audience type (Player, ServerPlayerEntity, etc.)
 * @param <B> log builder type
 */
public interface PrefixedLoggerAdapter<P, B extends LogBuilderCore> {
    /**
     * Returns prefixed logger core.
     *
     * @return core instance
     */
    PrefixedLoggerCore getCore();

    /**
     * Returns the parent logger adapter.
     *
     * @return logger adapter
     */
    LoggerAdapter<P, ?> getLoggerAdapter();

    /**
     * Creates a log builder for a log level.
     *
     * @param level log level
     * @return log builder instance
     */
    B buildLogBuilder(LogLevel level);

    /**
     * Returns prefixed logger name.
     *
     * @return logger name
     */
    default String getName() {
        return getCore().getName();
    }

    /**
     * Returns configured prefix.
     *
     * @return prefix text
     */
    default String getPrefix() {
        return getCore().getPrefix();
    }

    /**
     * Returns whether logger is enabled.
     *
     * @return true if enabled
     */
    default boolean isEnabled() {
        return getCore().isEnabled();
    }

    /**
     * Enables or disables this logger.
     *
     * @param enabled true to enable
     */
    default void setEnabled(boolean enabled) {
        getCore().setEnabled(enabled);
    }

    /**
     * Sends a message using default target.
     *
     * @param level log level
     * @param message message to send
     */
    default void send(LogLevel level, Object message) {
        getCore().send(level, message, null, null, getLoggerAdapter().getDefaultTarget(), false);
    }

    /**
     * Sends a message to a player.
     *
     * @param level log level
     * @param message message to send
     * @param player target player
     */
    default void send(LogLevel level, Object message, @Nullable P player) {
        getCore().send(level, message, wrapAudience(player), null, LogTarget.CHAT, false);
    }

    /**
     * Sends a message to a player, optionally broadcasting.
     *
     * @param level log level
     * @param message message to send
     * @param player target player
     * @param all whether to broadcast to all players
     */
    default void send(LogLevel level, Object message, @Nullable P player, boolean all) {
        getCore().send(level, message, wrapAudience(player), null, getLoggerAdapter().getDefaultTarget(), all);
    }

    /**
     * Sends a message to console target.
     *
     * @param level log level
     * @param message message to send
     */
    default void sendToConsole(LogLevel level, Object message) {
        getCore().send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    /**
     * Sends a message to a collection of players.
     *
     * @param level log level
     * @param message message to send
     * @param players target players
     */
    default void sendToPlayers(LogLevel level, Object message, @Nullable Collection<? extends P> players) {
        getCore().send(level, message, null, wrapAudiences(players), LogTarget.CHAT, false);
    }

    /**
     * Creates a builder for INFO level.
     *
     * @return log builder
     */
    default B log() {
        return buildLogBuilder(LogLevel.INFO);
    }

    /**
     * Creates a builder with prefix disabled.
     *
     * @return log builder
     */
    default B noPrefix() {
        B builder = buildLogBuilder(LogLevel.INFO);
        if (builder != null) {
            builder.noPrefix();
        }
        return builder;
    }

    /**
     * Creates an INFO level builder.
     *
     * @return log builder
     */
    default B info() {
        return buildLogBuilder(LogLevel.INFO);
    }

    /**
     * Creates a WARN level builder.
     *
     * @return log builder
     */
    default B warn() {
        return buildLogBuilder(LogLevel.WARN);
    }

    /**
     * Creates an ERROR level builder.
     *
     * @return log builder
     */
    default B error() {
        return buildLogBuilder(LogLevel.ERROR);
    }

    /**
     * Creates a DEBUG level builder.
     *
     * @return log builder
     */
    default B debug() {
        return buildLogBuilder(LogLevel.DEBUG);
    }

    /**
     * Creates a SUCCESS level builder.
     *
     * @return log builder
     */
    default B success() {
        return buildLogBuilder(LogLevel.SUCCESS);
    }

    private Audience wrapAudience(@Nullable P player) {
        if (player == null) {
            return null;
        }
        return getLoggerAdapter().wrapAudience(player);
    }

    private Collection<? extends Audience> wrapAudiences(@Nullable Collection<? extends P> players) {
        if (players == null || players.isEmpty()) {
            return null;
        }
        return players.stream()
                .filter(Objects::nonNull)
                .map(this::wrapAudience)
                .filter(Objects::nonNull)
                .toList();
    }
}
