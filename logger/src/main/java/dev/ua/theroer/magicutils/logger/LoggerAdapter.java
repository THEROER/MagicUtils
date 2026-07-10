package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Shared logger adapter API for platform-specific wrappers.
 *
 * @param <P> platform audience type (Player, ServerPlayerEntity, etc.)
 * @param <L> prefixed logger implementation type
 */
public interface LoggerAdapter<P, L> {
    /**
     * Returns the underlying logger core.
     *
     * @return core logger instance
     */
    LoggerCore getCore();

    /**
     * Returns the cache of prefixed logger instances.
     *
     * @return prefixed logger map
     */
    Map<String, L> getPrefixedLoggers();

    /**
     * Creates a new prefixed logger wrapper.
     *
     * @param core prefixed core instance
     * @return prefixed logger
     */
    L buildPrefixedLogger(PrefixedLoggerCore core);

    /**
     * Wraps a platform player into a MagicUtils audience.
     *
     * @param player platform player
     * @return wrapped audience
     */
    Audience wrapAudience(P player);

    /**
     * Returns the active logger config.
     *
     * @return logger config
     */
    default LoggerConfig getConfig() {
        return getCore().getConfig();
    }

    /**
     * Returns MiniMessage instance for formatting.
     *
     * @return MiniMessage instance
     */
    default MiniMessage getMiniMessage() {
        return getCore().getMiniMessage();
    }

    /**
     * Returns the placeholder owner key.
     *
     * @return owner key or null
     */
    default Object getPlaceholderOwner() {
        return getCore().getPlaceholderOwner();
    }

    /**
     * Returns external placeholder engine.
     *
     * @return external engine or null
     */
    default ExternalPlaceholderEngine getExternalPlaceholderEngine() {
        return getCore().getExternalPlaceholderEngine();
    }

    /**
     * Sets the external placeholder engine.
     *
     * @param engine engine instance
     */
    default void setExternalPlaceholderEngine(ExternalPlaceholderEngine engine) {
        getCore().setExternalPlaceholderEngine(engine);
    }

    /**
     * Controls whether external placeholders are escaped before MiniMessage
     * parsing. Disabled by default; enable it when placeholder values may
     * contain untrusted MiniMessage-like input.
     *
     * @param escapePlaceholders true to escape placeholder output
     */
    default void setEscapePlaceholders(boolean escapePlaceholders) {
        getCore().setEscapePlaceholders(escapePlaceholders);
    }

    /**
     * Returns language manager used for localization.
     *
     * @return language manager
     */
    default LanguageManager getLanguageManager() {
        return getCore().getLanguageManager();
    }

    /**
     * Sets language manager used for localization.
     *
     * @param languageManager language manager
     */
    default void setLanguageManager(LanguageManager languageManager) {
        getCore().setLanguageManager(languageManager);
    }

    /**
     * Reloads logger configuration.
     */
    default void reload() {
        getCore().reload();
    }

    /**
     * Returns chat prefix mode.
     *
     * @return chat prefix mode
     */
    default PrefixMode getChatPrefixMode() {
        return getCore().getChatPrefixMode();
    }

    /**
     * Returns console prefix mode.
     *
     * @return console prefix mode
     */
    default PrefixMode getConsolePrefixMode() {
        return getCore().getConsolePrefixMode();
    }

    /**
     * Returns custom prefix override.
     *
     * @return prefix string
     */
    default String getCustomPrefix() {
        return getCore().getCustomPrefix();
    }

    /**
     * Returns the default log target.
     *
     * @return default target
     */
    default LogTarget getDefaultTarget() {
        return getCore().getDefaultTarget();
    }

    /**
     * Returns whether the supplied log level is enabled for the default target.
     *
     * @param level log level to check
     * @return true when formatting and delivery should proceed
     */
    default boolean isLevelEnabled(LogLevel level) {
        return getCore().isLevelEnabled(level);
    }

    /**
     * Returns whether console formatting is stripped.
     *
     * @return true if formatting is stripped
     */
    default boolean isConsoleStripFormatting() {
        return getCore().isConsoleStripFormatting();
    }

    /**
     * Returns whether console gradients are enabled.
     *
     * @return true if gradients are enabled
     */
    default boolean isConsoleUseGradient() {
        return getCore().isConsoleUseGradient();
    }

    /**
     * Resolves color palette for a log level.
     *
     * @param level log level
     * @param forConsole true to resolve console colors
     * @return array of color strings
     */
    default String[] resolveColorsForLevel(LogLevel level, boolean forConsole) {
        return getCore().resolveColorsForLevel(level, forConsole);
    }

    /**
     * Parses message into a component using logger pipeline.
     *
     * @param message message input
     * @param level log level
     * @param target log target
     * @param player direct player target
     * @param players collection of target players
     * @param placeholdersArgs placeholder arguments
     * @return parsed component
     */
    default Component parseMessage(Object message,
                                   LogLevel level,
                                   LogTarget target,
                                   @Nullable P player,
                                   @Nullable Collection<? extends P> players,
                                   Object... placeholdersArgs) {
        Audience directAudience = player != null ? wrapAudience(player) : null;
        Collection<? extends Audience> audienceCollection = wrapAudiences(players);
        return getCore().parseMessage(message, level, target, directAudience, audienceCollection, placeholdersArgs);
    }

    /**
     * Parses message into a component using logger pipeline without prefix.
     *
     * @param message message input
     * @param player direct player target
     * @param players collection of target players
     * @param placeholdersArgs placeholder arguments
     * @return parsed component without prefix
     */
    default Component parseMessage(Object message,
                                   @Nullable P player,
                                   @Nullable Collection<? extends P> players,
                                   Object... placeholdersArgs) {
        Audience directAudience = player != null ? wrapAudience(player) : null;
        Collection<? extends Audience> audienceCollection = wrapAudiences(players);
        return getCore().parseMessage(message, directAudience, audienceCollection, placeholdersArgs);
    }

    /**
     * Parses MiniMessage or legacy text into a component.
     *
     * @param input input string
     * @return parsed component
     */
    default Component parseSmart(String input) {
        return MessageParser.parseSmart(input);
    }

    /**
     * Parses message into a component using logger pipeline without prefix for a single player.
     *
     * @param message message input
     * @param player direct player target
     * @param placeholdersArgs placeholder arguments
     * @return parsed component without prefix
     */
    default Component parseMessage(Object message,
                                   @Nullable P player,
                                   Object... placeholdersArgs) {
        return parseMessage(message, player, null, placeholdersArgs);
    }

    /**
     * Parses message into a component using logger pipeline without prefix for a player collection.
     *
     * @param message message input
     * @param players collection of target players
     * @param placeholdersArgs placeholder arguments
     * @return parsed component without prefix
     */
    default Component parseMessage(Object message,
                                   @Nullable Collection<? extends P> players,
                                   Object... placeholdersArgs) {
        return parseMessage(message, null, players, placeholdersArgs);
    }

    /**
     * Creates a prefixed logger with default prefix.
     *
     * @param name logger name
     * @return prefixed logger
     */
    default L create(String name) {
        return withPrefix(name);
    }

    /**
     * Creates a prefixed logger with custom prefix.
     *
     * @param name logger name
     * @param prefix prefix text
     * @return prefixed logger
     */
    default L create(String name, String prefix) {
        return withPrefix(name, prefix);
    }

    /**
     * Creates or retrieves a prefixed logger with a default bracketed prefix.
     *
     * @param name logger name
     * @return prefixed logger
     */
    default L withPrefix(String name) {
        return withPrefix(name, "[" + name + "]");
    }

    /**
     * Creates or retrieves a prefixed logger with the provided prefix.
     *
     * @param name logger name
     * @param prefix prefix text
     * @return prefixed logger
     */
    default L withPrefix(String name, String prefix) {
        return getPrefixedLoggers().computeIfAbsent(name, key -> {
            PrefixedLoggerCore prefixedCore = getCore().withPrefix(name, prefix);
            return buildPrefixedLogger(prefixedCore);
        });
    }

    /**
     * Broadcasts a message to console and players.
     *
     * @param message message to send
     */
    default void broadcast(Object message) {
        send(LogLevel.INFO, message, null, null, LogTarget.BOTH, true);
    }

    /**
     * Enables or disables a prefixed logger by name.
     *
     * @param name logger name
     * @param enabled whether logging is enabled
     */
    default void setPrefixedLoggerEnabled(String name, boolean enabled) {
        getCore().setPrefixedLoggerEnabled(name, enabled);
    }

    /**
     * Sends a message using the configured logger pipeline.
     *
     * @param level log level
     * @param message message to send
     * @param player direct player target
     * @param players player collection target
     * @param target log target
     * @param broadcast whether to broadcast to all players
     * @param placeholders placeholder arguments
     */
    default void send(LogLevel level,
                      Object message,
                      @Nullable P player,
                      @Nullable Collection<? extends P> players,
                      LogTarget target,
                      boolean broadcast,
                      Object... placeholders) {
        Audience directAudience = player != null ? wrapAudience(player) : null;
        Collection<? extends Audience> audienceCollection = wrapAudiences(players);
        getCore().send(level, message, directAudience, audienceCollection, target, broadcast, placeholders);
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
