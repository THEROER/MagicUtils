package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Map;

/**
 * Static language helper using LanguageManager.
 */
public class Messages {
    @Setter @Getter
    private static LanguageManager languageManager;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages() {
    }

    /**
     * Resolve a raw string without MiniMessage parsing.
     *
     * @param key message key
     * @return raw string or key if missing
     */
    public static String getRaw(String key) {
        return languageManager != null ? languageManager.getMessage(key) : key;
    }

    /**
     * Resolve a raw string with positional replacements.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string
     */
    public static String getRaw(String key, String... replacements) {
        return languageManager != null ? languageManager.getMessage(key, replacements) : key;
    }

    /**
     * Resolve a raw string with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string
     */
    public static String getRaw(String key, Map<String, String> placeholders) {
        return languageManager != null ? languageManager.getMessage(key, placeholders) : key;
    }

    /**
     * Resolve a raw string respecting sender language if available.
     *
     * @param sender audience or player-like object
     * @param key message key
     * @return resolved string
     */
    public static String getRaw(Object sender, String key) {
        if (sender instanceof Audience audience) {
            return getRaw(audience, key);
        }
        UUID id = extractUuid(sender);
        if (languageManager != null && id != null) {
            return languageManager.getMessageForLanguage(languageManager.getPlayerLanguage(id), key);
        }
        return getRaw(key);
    }

    /**
     * Resolve a raw string with replacements respecting sender language if available.
     *
     * @param sender audience or player-like object
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string
     */
    public static String getRaw(Object sender, String key, String... replacements) {
        if (sender instanceof Audience audience) {
            return getRaw(audience, key, replacements);
        }
        UUID id = extractUuid(sender);
        if (languageManager != null && id != null) {
            return languageManager.getMessageForLanguage(languageManager.getPlayerLanguage(id), key, replacements);
        }
        return getRaw(key, replacements);
    }

    private static UUID extractUuid(Object obj) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod("getUniqueId");
            Object res = m.invoke(obj);
            if (res instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
        return null;
    }

    /**
     * Resolve a raw string for an audience.
     *
     * @param audience target audience
     * @param key message key
     * @return resolved string
     */
    public static String getRaw(Audience audience, String key) {
        if (languageManager == null) {
            return key;
        }
        return languageManager.getMessageForAudience(audience, key);
    }

    /**
     * Resolve a raw string for an audience with replacements.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string
     */
    public static String getRaw(Audience audience, String key, String... replacements) {
        if (languageManager == null) {
            return key;
        }
        return languageManager.getMessageForAudience(audience, key, replacements);
    }

    /**
     * Resolve a raw string for an audience with placeholders.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string
     */
    public static String getRaw(Audience audience, String key, Map<String, String> placeholders) {
        if (languageManager == null) {
            return key;
        }
        return languageManager.getMessageForAudience(audience, key, placeholders);
    }

    /**
     * Resolve and deserialize a MiniMessage component.
     *
     * @param key message key
     * @return component
     */
    public static Component get(String key) {
        String raw = getRaw(key);
        return miniMessage.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component with replacements.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return component
     */
    public static Component get(String key, String... replacements) {
        String raw = languageManager != null ? languageManager.getMessageEscaped(key, replacements) : key;
        return miniMessage.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return component
     */
    public static Component get(String key, Map<String, String> placeholders) {
        String raw = languageManager != null ? languageManager.getMessageEscaped(key, placeholders) : key;
        return miniMessage.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component for an audience.
     *
     * @param audience target audience
     * @param key message key
     * @return component
     */
    public static Component get(Audience audience, String key) {
        String raw = getRaw(audience, key);
        return miniMessage.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component for an audience with replacements.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return component
     */
    public static Component get(Audience audience, String key, String... replacements) {
        String raw = languageManager != null ? languageManager.getMessageForAudienceEscaped(audience, key, replacements) : key;
        return miniMessage.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component for an audience with placeholders.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return component
     */
    public static Component get(Audience audience, String key, Map<String, String> placeholders) {
        String raw = languageManager != null ? languageManager.getMessageForAudienceEscaped(audience, key, placeholders) : key;
        return miniMessage.deserialize(raw);
    }

    /**
     * Send a component to an audience.
     *
     * @param audience target audience
     * @param key message key
     */
    public static void send(Audience audience, String key) {
        audience.send(get(audience, key));
    }

    /**
     * Send a component with replacements to an audience.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     */
    public static void send(Audience audience, String key, String... replacements) {
        audience.send(get(audience, key, replacements));
    }

    /**
     * Send a component with placeholders to an audience.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     */
    public static void send(Audience audience, String key, Map<String, String> placeholders) {
        audience.send(get(audience, key, placeholders));
    }

    /**
     * Check whether a message exists in current/fallback language.
     *
     * @param key message key
     * @return true if present
     */
    public static boolean exists(String key) {
        return languageManager != null && languageManager.hasMessage(key);
    }

    /**
     * Get active language code (falls back to "en" when manager is absent).
     *
     * @return current language code or "en" if manager is missing
     */
    public static String getCurrentLanguage() {
        return languageManager != null ? languageManager.getCurrentLanguage() : "en";
    }
}
