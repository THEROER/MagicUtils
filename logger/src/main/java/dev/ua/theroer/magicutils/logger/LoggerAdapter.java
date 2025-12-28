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
@SuppressWarnings("doclint:missing")
public interface LoggerAdapter<P, L> {
    LoggerCore getCore();

    Map<String, L> getPrefixedLoggers();

    L buildPrefixedLogger(PrefixedLoggerCore core);

    Audience wrapAudience(P player);

    default LoggerConfig getConfig() {
        return getCore().getConfig();
    }

    default MiniMessage getMiniMessage() {
        return getCore().getMiniMessage();
    }

    default Object getPlaceholderOwner() {
        return getCore().getPlaceholderOwner();
    }

    default ExternalPlaceholderEngine getExternalPlaceholderEngine() {
        return getCore().getExternalPlaceholderEngine();
    }

    default void setExternalPlaceholderEngine(ExternalPlaceholderEngine engine) {
        getCore().setExternalPlaceholderEngine(engine);
    }

    default LanguageManager getLanguageManager() {
        return getCore().getLanguageManager();
    }

    default void setLanguageManager(LanguageManager languageManager) {
        getCore().setLanguageManager(languageManager);
    }

    default void setAutoLocalization(boolean enabled) {
        getCore().setAutoLocalization(enabled);
    }

    default void reload() {
        getCore().reload();
    }

    default PrefixMode getChatPrefixMode() {
        return getCore().getChatPrefixMode();
    }

    default void setChatPrefixMode(PrefixMode mode) {
        getCore().setChatPrefixMode(mode);
    }

    default PrefixMode getConsolePrefixMode() {
        return getCore().getConsolePrefixMode();
    }

    default void setConsolePrefixMode(PrefixMode mode) {
        getCore().setConsolePrefixMode(mode);
    }

    default String getCustomPrefix() {
        return getCore().getCustomPrefix();
    }

    default void setCustomPrefix(String customPrefix) {
        getCore().setCustomPrefix(customPrefix);
    }

    default LogTarget getDefaultTarget() {
        return getCore().getDefaultTarget();
    }

    default void setDefaultTarget(LogTarget target) {
        getCore().setDefaultTarget(target);
    }

    default boolean isConsoleStripFormatting() {
        return getCore().isConsoleStripFormatting();
    }

    default void setConsoleStripFormatting(boolean consoleStripFormatting) {
        getCore().setConsoleStripFormatting(consoleStripFormatting);
    }

    default boolean isConsoleUseGradient() {
        return getCore().isConsoleUseGradient();
    }

    default void setConsoleUseGradient(boolean consoleUseGradient) {
        getCore().setConsoleUseGradient(consoleUseGradient);
    }

    default String[] resolveColorsForLevel(LogLevel level, boolean forConsole) {
        return getCore().resolveColorsForLevel(level, forConsole);
    }

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

    default Component parseSmart(String input) {
        return MessageParser.parseSmart(input);
    }

    default L create(String name) {
        return withPrefix(name);
    }

    default L create(String name, String prefix) {
        return withPrefix(name, prefix);
    }

    default L withPrefix(String name) {
        return withPrefix(name, "[" + name + "]");
    }

    default L withPrefix(String name, String prefix) {
        return getPrefixedLoggers().computeIfAbsent(name, key -> {
            PrefixedLoggerCore prefixedCore = getCore().withPrefix(name, prefix);
            return buildPrefixedLogger(prefixedCore);
        });
    }

    default void broadcast(Object message) {
        send(LogLevel.INFO, message, null, null, LogTarget.BOTH, true);
    }

    default void setPrefixedLoggerEnabled(String name, boolean enabled) {
        getCore().setPrefixedLoggerEnabled(name, enabled);
    }

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
