package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.platform.Audience;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves command descriptions, optionally treating {@code @key} values as language keys.
 */
public final class CommandDescriptions {
    private static final char TRANSLATION_PREFIX = '@';

    private CommandDescriptions() {
    }

    /**
     * Resolves a command description for global metadata such as registration and schema inspection.
     *
     * @param languageScope plugin/mod scope used for scoped language managers
     * @param description raw description or {@code @key}
     * @return resolved description text
     */
    public static String resolveGlobal(@Nullable String languageScope, @Nullable String description) {
        return resolve(findLanguageManager(languageScope), null, description, null);
    }

    /**
     * Resolves a command description for global metadata such as registration and schema inspection,
     * falling back to the implicit {@code commands.<name>.description} key when the description is blank.
     *
     * @param languageScope plugin/mod scope used for scoped language managers
     * @param description raw description or {@code @key}
     * @param commandName root command name
     * @return resolved description text
     */
    public static String resolveGlobal(@Nullable String languageScope,
                                       @Nullable String description,
                                       @Nullable String commandName) {
        return resolveGlobal(languageScope, description, commandName, List.of());
    }

    /**
     * Resolves a command description for global metadata such as registration and schema inspection,
     * falling back to the implicit {@code commands.<command>[.<subcommand>...].description} key
     * when the description is blank.
     *
     * @param languageScope plugin/mod scope used for scoped language managers
     * @param description raw description or {@code @key}
     * @param commandName root command name
     * @param subCommandPath relative subcommand path
     * @return resolved description text
     */
    public static String resolveGlobal(@Nullable String languageScope,
                                       @Nullable String description,
                                       @Nullable String commandName,
                                       @Nullable List<String> subCommandPath) {
        return resolve(findLanguageManager(languageScope), null, description,
                defaultDescriptionKey(commandName, subCommandPath));
    }

    /**
     * Resolves a command description for a specific audience, respecting per-player language overrides.
     *
     * @param languageScope plugin/mod scope used for scoped language managers
     * @param audience recipient audience
     * @param description raw description or {@code @key}
     * @return resolved description text
     */
    public static String resolveForAudience(@Nullable String languageScope,
                                            @Nullable Audience audience,
                                            @Nullable String description) {
        return resolve(findLanguageManager(languageScope), audience, description, null);
    }

    /**
     * Resolves a command description for a specific audience, respecting per-player language overrides,
     * and falling back to the implicit {@code commands.<name>.description} key when the description is blank.
     *
     * @param languageScope plugin/mod scope used for scoped language managers
     * @param audience recipient audience
     * @param description raw description or {@code @key}
     * @param commandName root command name
     * @return resolved description text
     */
    public static String resolveForAudience(@Nullable String languageScope,
                                            @Nullable Audience audience,
                                            @Nullable String description,
                                            @Nullable String commandName) {
        return resolveForAudience(languageScope, audience, description, commandName, List.of());
    }

    /**
     * Resolves a command description for a specific audience, respecting per-player language overrides,
     * and falling back to the implicit {@code commands.<command>[.<subcommand>...].description} key
     * when the description is blank.
     *
     * @param languageScope plugin/mod scope used for scoped language managers
     * @param audience recipient audience
     * @param description raw description or {@code @key}
     * @param commandName root command name
     * @param subCommandPath relative subcommand path
     * @return resolved description text
     */
    public static String resolveForAudience(@Nullable String languageScope,
                                            @Nullable Audience audience,
                                            @Nullable String description,
                                            @Nullable String commandName,
                                            @Nullable List<String> subCommandPath) {
        return resolve(findLanguageManager(languageScope), audience, description,
                defaultDescriptionKey(commandName, subCommandPath));
    }

    /**
     * Returns whether the description uses the {@code @key} localisation syntax.
     *
     * @param description description to inspect
     * @return true when the value should be resolved through {@link LanguageManager}
     */
    public static boolean isTranslationKey(@Nullable String description) {
        return description != null
                && description.length() > 1
                && description.charAt(0) == TRANSLATION_PREFIX;
    }

    /**
     * Builds the implicit description translation key for a root command.
     *
     * @param commandName root command name
     * @return translation key or null when the name is blank
     */
    public static @Nullable String defaultDescriptionKey(@Nullable String commandName) {
        return defaultDescriptionKey(commandName, List.of());
    }

    /**
     * Builds the implicit description translation key for a command or subcommand.
     *
     * <p>The key format is {@code commands.<command>[.<subcommand>...].description}.</p>
     *
     * @param commandName root command name
     * @param subCommandPath relative subcommand path
     * @return translation key or null when the command name is blank
     */
    public static @Nullable String defaultDescriptionKey(@Nullable String commandName,
                                                         @Nullable List<String> subCommandPath) {
        List<String> segments = new ArrayList<>();
        appendSegment(segments, commandName);
        if (segments.isEmpty()) {
            return null;
        }
        if (subCommandPath != null) {
            for (String part : subCommandPath) {
                appendSegment(segments, part);
            }
        }
        return "commands." + String.join(".", segments) + ".description";
    }

    private static @Nullable LanguageManager findLanguageManager(@Nullable String languageScope) {
        LanguageManager manager = null;
        if (languageScope != null && !languageScope.isBlank()) {
            manager = Messages.getLanguageManager(languageScope);
        }
        return manager != null ? manager : Messages.getLanguageManager();
    }

    private static String resolve(@Nullable LanguageManager manager,
                                  @Nullable Audience audience,
                                  @Nullable String description,
                                  @Nullable String implicitKey) {
        if (description == null || description.isEmpty()) {
            return resolveImplicit(manager, audience, implicitKey);
        }
        if (!isTranslationKey(description) || manager == null) {
            return description;
        }
        String key = description.substring(1);
        return audience != null ? manager.getMessageFor(audience, key) : manager.getMessage(key);
    }

    private static String resolveImplicit(@Nullable LanguageManager manager,
                                          @Nullable Audience audience,
                                          @Nullable String implicitKey) {
        if (manager == null || implicitKey == null || implicitKey.isEmpty()) {
            return "";
        }
        String resolved = audience != null
                ? manager.getMessageFor(audience, implicitKey)
                : manager.getMessage(implicitKey);
        return implicitKey.equals(resolved) ? "" : resolved;
    }

    private static void appendSegment(List<String> segments, @Nullable String raw) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        segments.add(trimmed.toLowerCase(Locale.ROOT));
    }
}
