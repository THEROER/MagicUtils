package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static language helper using LanguageManager.
 */
public class Messages {
    private static final String DEFAULT_SCOPE = "default";
    private static final Map<String, LanguageManager> LANGUAGE_MANAGERS = new ConcurrentHashMap<>();
    private static volatile LanguageManager languageManager;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages() {
    }

    /**
     * Sets the default global language manager (legacy).
     *
     * @param manager language manager to use as default
     */
    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
        if (manager != null) {
            LANGUAGE_MANAGERS.put(DEFAULT_SCOPE, manager);
        } else {
            LANGUAGE_MANAGERS.remove(DEFAULT_SCOPE);
        }
    }

    /**
     * Returns the default global language manager.
     *
     * @return language manager or null
     */
    public static LanguageManager getLanguageManager() {
        return languageManager;
    }

    /**
     * Registers a language manager for a scope (plugin/mod name).
     *
     * @param scope scope identifier
     * @param manager language manager to register
     */
    public static void register(String scope, LanguageManager manager) {
        String key = normalizeScope(scope);
        if (manager == null) {
            LANGUAGE_MANAGERS.remove(key);
            if (DEFAULT_SCOPE.equals(key)) {
                languageManager = null;
            }
            return;
        }
        LANGUAGE_MANAGERS.put(key, manager);
        if (DEFAULT_SCOPE.equals(key)) {
            languageManager = manager;
        }
    }

    /**
     * Unregisters a language manager for a scope.
     *
     * @param scope scope identifier
     */
    public static void unregister(String scope) {
        register(scope, null);
    }

    /**
     * Returns the language manager for a scope.
     *
     * @param scope scope identifier
     * @return language manager or null
     */
    public static LanguageManager getLanguageManager(String scope) {
        return LANGUAGE_MANAGERS.get(normalizeScope(scope));
    }

    /**
     * Returns a scoped view for message resolution.
     *
     * @param scope scope identifier
     * @return messages view
     */
    public static MessagesView view(String scope) {
        return new MessagesView(normalizeScope(scope));
    }

    /**
     * Returns a view bound to a specific language manager.
     *
     * @param manager language manager
     * @return messages view
     */
    public static MessagesView view(LanguageManager manager) {
        return new MessagesView(manager);
    }

    private static String normalizeScope(String scope) {
        String normalized = scope != null ? scope.trim().toLowerCase(Locale.ROOT) : "";
        return normalized.isEmpty() ? DEFAULT_SCOPE : normalized;
    }

    /**
     * Resolve a raw string without MiniMessage parsing.
     *
     * @param key message key
     * @return raw string or key if missing
     */
    public static String getRaw(String key) {
        LanguageManager manager = getLanguageManager();
        return manager != null ? manager.getMessage(key) : key;
    }

    /**
     * Resolve a raw string with positional replacements.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string
     */
    public static String getRaw(String key, String... replacements) {
        LanguageManager manager = getLanguageManager();
        return manager != null ? manager.getMessage(key, replacements) : key;
    }

    /**
     * Resolve a raw string with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string
     */
    public static String getRaw(String key, Map<String, String> placeholders) {
        LanguageManager manager = getLanguageManager();
        return manager != null ? manager.getMessage(key, placeholders) : key;
    }

    /**
     * Resolve a raw string with named placeholders escaped for MiniMessage parsing.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string with escaped placeholder values
     */
    public static String getRawEscaped(String key, Map<String, String> placeholders) {
        LanguageManager manager = getLanguageManager();
        return manager != null ? manager.getMessageEscaped(key, placeholders) : key;
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
        LanguageManager manager = getLanguageManager();
        if (manager != null && id != null) {
            return manager.getMessageForLanguage(manager.getPlayerLanguage(id), key);
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
        LanguageManager manager = getLanguageManager();
        if (manager != null && id != null) {
            return manager.getMessageForLanguage(manager.getPlayerLanguage(id), key, replacements);
        }
        return getRaw(key, replacements);
    }

    /**
     * Resolve a raw string with replacements respecting sender language if available,
     * escaping replacement values for MiniMessage parsing.
     *
     * @param sender audience or player-like object
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string with escaped replacement values
     */
    public static String getRawEscaped(Object sender, String key, String... replacements) {
        if (sender instanceof Audience audience) {
            return getRawEscaped(audience, key, replacements);
        }
        UUID id = extractUuid(sender);
        LanguageManager manager = getLanguageManager();
        if (manager != null && id != null) {
            return manager.getMessageForLanguageEscaped(manager.getPlayerLanguage(id), key, replacements);
        }
        return getRawEscaped(key, replacements);
    }

    private static UUID extractUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Audience audience) {
            return audience.id();
        }
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
        LanguageManager manager = getLanguageManager();
        if (manager == null) {
            return key;
        }
        return manager.getMessageForAudience(audience, key);
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
        LanguageManager manager = getLanguageManager();
        if (manager == null) {
            return key;
        }
        return manager.getMessageForAudience(audience, key, replacements);
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
        LanguageManager manager = getLanguageManager();
        if (manager == null) {
            return key;
        }
        return manager.getMessageForAudience(audience, key, placeholders);
    }

    /**
     * Resolve a raw string with positional replacements escaped for MiniMessage parsing.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string with escaped replacement values
     */
    public static String getRawEscaped(String key, String... replacements) {
        LanguageManager manager = getLanguageManager();
        return manager != null ? manager.getMessageEscaped(key, replacements) : key;
    }

    /**
     * Resolve a raw string for an audience with escaped replacement values for MiniMessage parsing.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string with escaped replacement values
     */
    public static String getRawEscaped(Audience audience, String key, String... replacements) {
        LanguageManager manager = getLanguageManager();
        if (manager == null) {
            return key;
        }
        return manager.getMessageForAudienceEscaped(audience, key, replacements);
    }

    /**
     * Resolve a raw string for an audience with escaped placeholder values for MiniMessage parsing.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string with escaped placeholder values
     */
    public static String getRawEscaped(Audience audience, String key, Map<String, String> placeholders) {
        LanguageManager manager = getLanguageManager();
        if (manager == null) {
            return key;
        }
        return manager.getMessageForAudienceEscaped(audience, key, placeholders);
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
        LanguageManager manager = getLanguageManager();
        String raw = manager != null ? manager.getMessageEscaped(key, replacements) : key;
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
        LanguageManager manager = getLanguageManager();
        String raw = manager != null ? manager.getMessageEscaped(key, placeholders) : key;
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
        LanguageManager manager = getLanguageManager();
        String raw = manager != null ? manager.getMessageForAudienceEscaped(audience, key, replacements) : key;
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
        LanguageManager manager = getLanguageManager();
        String raw = manager != null ? manager.getMessageForAudienceEscaped(audience, key, placeholders) : key;
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
        LanguageManager manager = getLanguageManager();
        return manager != null && manager.hasMessage(key);
    }

    /**
     * Get active language code (falls back to "en" when manager is absent).
     *
     * @return current language code or "en" if manager is missing
     */
    public static String getCurrentLanguage() {
        LanguageManager manager = getLanguageManager();
        return manager != null ? manager.getCurrentLanguage() : "en";
    }
}
