package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Map;

/**
 * Static language helper using LanguageManager.
 */
public class Messages {
    private static LanguageManager languageManager;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages() {
    }

    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
    }

    public static LanguageManager getLanguageManager() {
        return languageManager;
    }

    public static String getRaw(String key) {
        return languageManager != null ? languageManager.getMessage(key) : key;
    }

    public static String getRaw(String key, String... replacements) {
        return languageManager != null ? languageManager.getMessage(key, replacements) : key;
    }

    public static String getRaw(String key, Map<String, String> placeholders) {
        return languageManager != null ? languageManager.getMessage(key, placeholders) : key;
    }

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
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String getRaw(Audience audience, String key) {
        if (languageManager == null) {
            return key;
        }
        return languageManager.getMessageForAudience(audience, key);
    }

    public static String getRaw(Audience audience, String key, String... replacements) {
        if (languageManager == null) {
            return key;
        }
        return languageManager.getMessageForAudience(audience, key, replacements);
    }

    public static String getRaw(Audience audience, String key, Map<String, String> placeholders) {
        if (languageManager == null) {
            return key;
        }
        return languageManager.getMessageForAudience(audience, key, placeholders);
    }

    public static Component get(String key) {
        String raw = getRaw(key);
        return miniMessage.deserialize(raw);
    }

    public static Component get(String key, String... replacements) {
        String raw = getRaw(key, replacements);
        return miniMessage.deserialize(raw);
    }

    public static Component get(String key, Map<String, String> placeholders) {
        String raw = getRaw(key, placeholders);
        return miniMessage.deserialize(raw);
    }

    public static Component get(Audience audience, String key) {
        String raw = getRaw(audience, key);
        return miniMessage.deserialize(raw);
    }

    public static Component get(Audience audience, String key, String... replacements) {
        String raw = getRaw(audience, key, replacements);
        return miniMessage.deserialize(raw);
    }

    public static Component get(Audience audience, String key, Map<String, String> placeholders) {
        String raw = getRaw(audience, key, placeholders);
        return miniMessage.deserialize(raw);
    }

    public static void send(Audience audience, String key) {
        audience.send(get(audience, key));
    }

    public static void send(Audience audience, String key, String... replacements) {
        audience.send(get(audience, key, replacements));
    }

    public static void send(Audience audience, String key, Map<String, String> placeholders) {
        audience.send(get(audience, key, placeholders));
    }

    public static boolean exists(String key) {
        return languageManager != null && languageManager.hasMessage(key);
    }

    public static String getCurrentLanguage() {
        return languageManager != null ? languageManager.getCurrentLanguage() : "en";
    }
}
