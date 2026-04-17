package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Scoped view for message resolution.
 */
public final class MessagesView {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final String scope;
    private final LanguageManager manager;

    MessagesView(String scope) {
        this.scope = scope;
        this.manager = null;
    }

    MessagesView(LanguageManager manager) {
        this.scope = null;
        this.manager = manager;
    }

    /**
     * Returns the scope identifier (nullable when bound directly).
     *
     * @return scope id or null
     */
    public String scope() {
        return scope;
    }

    /**
     * Returns the resolved language manager for this view.
     *
     * @return language manager or null
     */
    public LanguageManager getLanguageManager() {
        return resolveManager();
    }

    private LanguageManager resolveManager() {
        if (manager != null) {
            return manager;
        }
        if (scope != null) {
            return Messages.getLanguageManager(scope);
        }
        return Messages.getLanguageManager();
    }

    /**
     * Resolve a raw string without MiniMessage parsing.
     *
     * @param key message key
     * @return raw string or key if missing
     */
    public String getRaw(String key) {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getMessage(key) : key;
    }

    /**
     * Resolve a raw string with positional replacements.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string
     */
    public String getRaw(String key, String... replacements) {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getMessage(key, replacements) : key;
    }

    /**
     * Resolve a raw string with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string
     */
    public String getRaw(String key, Map<String, String> placeholders) {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getMessage(key, placeholders) : key;
    }

    /**
     * Resolve a raw string with named placeholders escaped for MiniMessage parsing.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string with escaped placeholder values
     */
    public String getRawEscaped(String key, Map<String, String> placeholders) {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getMessageEscaped(key, placeholders) : key;
    }

    /**
     * Resolve a raw string respecting sender language if available.
     *
     * @param sender audience or player-like object
     * @param key message key
     * @return resolved string
     */
    public String getRaw(Object sender, String key) {
        if (sender instanceof Audience audience) {
            return getRaw(audience, key);
        }
        UUID id = extractUuid(sender);
        LanguageManager resolved = resolveManager();
        if (resolved != null && id != null) {
            return resolved.getMessageForLanguage(resolved.getPlayerLanguage(id), key);
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
    public String getRaw(Object sender, String key, String... replacements) {
        if (sender instanceof Audience audience) {
            return getRaw(audience, key, replacements);
        }
        UUID id = extractUuid(sender);
        LanguageManager resolved = resolveManager();
        if (resolved != null && id != null) {
            return resolved.getMessageForLanguage(resolved.getPlayerLanguage(id), key, replacements);
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
    public String getRawEscaped(Object sender, String key, String... replacements) {
        if (sender instanceof Audience audience) {
            return getRawEscaped(audience, key, replacements);
        }
        UUID id = extractUuid(sender);
        LanguageManager resolved = resolveManager();
        if (resolved != null && id != null) {
            return resolved.getMessageForLanguageEscaped(resolved.getPlayerLanguage(id), key, replacements);
        }
        return getRawEscaped(key, replacements);
    }

    private static UUID extractUuid(Object obj) {
        if (obj == null) {
            return null;
        }
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
    public String getRaw(Audience audience, String key) {
        LanguageManager resolved = resolveManager();
        if (resolved == null) {
            return key;
        }
        return resolved.getMessageForAudience(audience, key);
    }

    /**
     * Resolve a raw string for an audience with replacements.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string
     */
    public String getRaw(Audience audience, String key, String... replacements) {
        LanguageManager resolved = resolveManager();
        if (resolved == null) {
            return key;
        }
        return resolved.getMessageForAudience(audience, key, replacements);
    }

    /**
     * Resolve a raw string for an audience with placeholders.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string
     */
    public String getRaw(Audience audience, String key, Map<String, String> placeholders) {
        LanguageManager resolved = resolveManager();
        if (resolved == null) {
            return key;
        }
        return resolved.getMessageForAudience(audience, key, placeholders);
    }

    /**
     * Resolve a raw string with positional replacements escaped for MiniMessage parsing.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string with escaped replacement values
     */
    public String getRawEscaped(String key, String... replacements) {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getMessageEscaped(key, replacements) : key;
    }

    /**
     * Resolve a raw string for an audience with escaped replacement values for MiniMessage parsing.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved string with escaped replacement values
     */
    public String getRawEscaped(Audience audience, String key, String... replacements) {
        LanguageManager resolved = resolveManager();
        if (resolved == null) {
            return key;
        }
        return resolved.getMessageForAudienceEscaped(audience, key, replacements);
    }

    /**
     * Resolve a raw string for an audience with escaped placeholder values for MiniMessage parsing.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved string with escaped placeholder values
     */
    public String getRawEscaped(Audience audience, String key, Map<String, String> placeholders) {
        LanguageManager resolved = resolveManager();
        if (resolved == null) {
            return key;
        }
        return resolved.getMessageForAudienceEscaped(audience, key, placeholders);
    }

    /**
     * Resolve and deserialize a MiniMessage component.
     *
     * @param key message key
     * @return component
     */
    public Component get(String key) {
        String raw = getRaw(key);
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component with replacements.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return component
     */
    public Component get(String key, String... replacements) {
        LanguageManager resolved = resolveManager();
        String raw = resolved != null ? resolved.getMessageEscaped(key, replacements) : key;
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return component
     */
    public Component get(String key, Map<String, String> placeholders) {
        LanguageManager resolved = resolveManager();
        String raw = resolved != null ? resolved.getMessageEscaped(key, placeholders) : key;
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component for an audience.
     *
     * @param audience target audience
     * @param key message key
     * @return component
     */
    public Component get(Audience audience, String key) {
        String raw = getRaw(audience, key);
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component for an audience with replacements.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return component
     */
    public Component get(Audience audience, String key, String... replacements) {
        LanguageManager resolved = resolveManager();
        String raw = resolved != null ? resolved.getMessageForAudienceEscaped(audience, key, replacements) : key;
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Resolve and deserialize a component for an audience with placeholders.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return component
     */
    public Component get(Audience audience, String key, Map<String, String> placeholders) {
        LanguageManager resolved = resolveManager();
        String raw = resolved != null ? resolved.getMessageForAudienceEscaped(audience, key, placeholders) : key;
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Send a component to an audience.
     *
     * @param audience target audience
     * @param key message key
     */
    public void send(Audience audience, String key) {
        audience.send(get(audience, key));
    }

    /**
     * Send a component with replacements to an audience.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     */
    public void send(Audience audience, String key, String... replacements) {
        audience.send(get(audience, key, replacements));
    }

    /**
     * Send a component with placeholders to an audience.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     */
    public void send(Audience audience, String key, Map<String, String> placeholders) {
        audience.send(get(audience, key, placeholders));
    }

    /**
     * Check whether a message exists in current/fallback language.
     *
     * @param key message key
     * @return true if present
     */
    public boolean exists(String key) {
        LanguageManager resolved = resolveManager();
        return resolved != null && resolved.hasMessage(key);
    }

    /**
     * Get active language code (falls back to "en" when manager is absent).
     *
     * @return current language code or "en" if manager is missing
     */
    public String getCurrentLanguage() {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getCurrentLanguage() : "en";
    }
}
