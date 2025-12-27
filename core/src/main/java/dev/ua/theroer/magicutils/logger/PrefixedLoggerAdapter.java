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
    PrefixedLoggerCore getCore();

    LoggerAdapter<P, ?> getLoggerAdapter();

    B buildLogBuilder(LogLevel level);

    default String getName() {
        return getCore().getName();
    }

    default String getPrefix() {
        return getCore().getPrefix();
    }

    default boolean isEnabled() {
        return getCore().isEnabled();
    }

    default void setEnabled(boolean enabled) {
        getCore().setEnabled(enabled);
    }

    default void send(LogLevel level, Object message) {
        getCore().send(level, message, null, null, getLoggerAdapter().getDefaultTarget(), false);
    }

    default void send(LogLevel level, Object message, @Nullable P player) {
        getCore().send(level, message, wrapAudience(player), null, LogTarget.CHAT, false);
    }

    default void send(LogLevel level, Object message, @Nullable P player, boolean all) {
        getCore().send(level, message, wrapAudience(player), null, getLoggerAdapter().getDefaultTarget(), all);
    }

    default void sendToConsole(LogLevel level, Object message) {
        getCore().send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    default void sendToPlayers(LogLevel level, Object message, @Nullable Collection<? extends P> players) {
        getCore().send(level, message, null, wrapAudiences(players), LogTarget.CHAT, false);
    }

    default B log() {
        return buildLogBuilder(LogLevel.INFO);
    }

    default B noPrefix() {
        B builder = buildLogBuilder(LogLevel.INFO);
        if (builder != null) {
            builder.noPrefix();
        }
        return builder;
    }

    default B info() {
        return buildLogBuilder(LogLevel.INFO);
    }

    default B warn() {
        return buildLogBuilder(LogLevel.WARN);
    }

    default B error() {
        return buildLogBuilder(LogLevel.ERROR);
    }

    default B debug() {
        return buildLogBuilder(LogLevel.DEBUG);
    }

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
