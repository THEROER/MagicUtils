package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.lang.messages.*;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Language configuration using the MagicUtils config system.
 *
 * <p>After load, a flat {@code key -> value} cache is built from all
 * configured sections so runtime {@link #getMessage(String)} lookups are
 * plain {@code Map.get} calls and never touch reflection.</p>
 */
@Getter
@ConfigFile("lang/{lang}.{ext}")
@Comment("Language file for MagicUtils")
public class LanguageConfig {

    private static final Map<Class<?>, Map<String, Field>> SECTION_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Function<LanguageConfig, Object>> SECTION_ACCESSORS;

    static {
        Map<String, Function<LanguageConfig, Object>> accessors = new LinkedHashMap<>();
        accessors.put("commands", LanguageConfig::getCommands);
        accessors.put("settings", LanguageConfig::getSettings);
        accessors.put("reload", LanguageConfig::getReload);
        accessors.put("system", LanguageConfig::getSystem);
        accessors.put("errors", LanguageConfig::getErrors);
        SECTION_ACCESSORS = Collections.unmodifiableMap(accessors);
    }

    /**
     * Default constructor for LanguageConfig.
     */
    public LanguageConfig() {
    }

    @ConfigSection("language")
    @Comment("Language metadata")
    private LanguageMetadata metadata = new LanguageMetadata();

    @ConfigSection("magicutils.commands")
    @Comment("Command related messages")
    private CommandMessages commands = new CommandMessages();

    @ConfigSection("magicutils.settings")
    @Comment("Settings command messages")
    private SettingsMessages settings = new SettingsMessages();

    @ConfigSection("magicutils.reload")
    @Comment("Reload command messages")
    private ReloadMessages reload = new ReloadMessages();

    @ConfigSection("magicutils.system")
    @Comment("System messages")
    private SystemMessages system = new SystemMessages();

    @ConfigSection("magicutils.errors")
    @Comment("Error messages")
    private ErrorMessages errors = new ErrorMessages();

    @ConfigValue("messages")
    @Comment("Custom messages defined by plugins")
    private Map<String, String> customMessages = new HashMap<>();

    private transient volatile Map<String, String> flatMessageCache;

    /**
     * Adds or updates a custom message entry.
     *
     * @param key message key
     * @param value message text (null removes the key)
     */
    public void putCustomMessage(String key, String value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (value == null) {
            customMessages.remove(key);
        } else {
            customMessages.put(key, value);
        }
    }

    /**
     * Resolves a message by key. Custom plugin messages take precedence
     * over the built-in sectioned messages.
     *
     * <p>Built-in keys follow the {@code magicutils.<section>.<field>}
     * pattern. The resolution uses a flat cache built lazily on first
     * access so there is no reflection on the hot path.</p>
     *
     * @param key message key
     * @return resolved value or {@code null} when no entry exists
     */
    public String getMessage(String key) {
        if (key == null) {
            return null;
        }
        String custom = customMessages.get(key);
        if (custom != null) {
            return custom;
        }
        return getFlatMessages().get(key);
    }

    private Map<String, String> getFlatMessages() {
        Map<String, String> cached = flatMessageCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (flatMessageCache == null) {
                flatMessageCache = buildFlatMessages();
            }
            return flatMessageCache;
        }
    }

    private Map<String, String> buildFlatMessages() {
        Map<String, String> flat = new HashMap<>();
        for (Map.Entry<String, Function<LanguageConfig, Object>> entry : SECTION_ACCESSORS.entrySet()) {
            Object section = entry.getValue().apply(this);
            if (section == null) {
                continue;
            }
            String prefix = "magicutils." + entry.getKey() + ".";
            Map<String, Field> fields = SECTION_FIELD_CACHE.computeIfAbsent(
                    section.getClass(), LanguageConfig::mapSectionFields);
            for (Map.Entry<String, Field> fieldEntry : fields.entrySet()) {
                try {
                    Object value = fieldEntry.getValue().get(section);
                    if (value != null) {
                        flat.put(prefix + fieldEntry.getKey(), value.toString());
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return Collections.unmodifiableMap(flat);
    }

    private void invalidateFlatCache() {
        flatMessageCache = null;
    }

    private static Map<String, Field> mapSectionFields(Class<?> type) {
        Map<String, Field> map = new HashMap<>();
        for (Field field : type.getDeclaredFields()) {
            ConfigValue annotation = field.getAnnotation(ConfigValue.class);
            if (annotation == null) {
                continue;
            }

            field.setAccessible(true);
            String name = annotation.value().isEmpty() ? field.getName() : annotation.value();
            map.put(name, field);
        }
        return map;
    }

    void applyTranslations(Map<String, Map<String, String>> translations, boolean override) {
        if (translations == null || translations.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<String, Map<String, String>> sectionEntry : translations.entrySet()) {
            String sectionPath = sectionEntry.getKey();
            Map<String, String> values = sectionEntry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }

            Object section = resolveSection(sectionPath);
            if (section == null) {
                continue;
            }
            Map<String, Field> fieldMap = SECTION_FIELD_CACHE.computeIfAbsent(section.getClass(),
                    LanguageConfig::mapSectionFields);
            for (Map.Entry<String, String> entry : values.entrySet()) {
                Field field = fieldMap.get(entry.getKey());
                if (field == null) {
                    continue;
                }
                try {
                    Object current = field.get(section);
                    if (!override && current != null) {
                        continue;
                    }
                    field.set(section, entry.getValue());
                    changed = true;
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        if (changed) {
            invalidateFlatCache();
        }
    }

    private Object resolveSection(String sectionPath) {
        if (sectionPath == null || sectionPath.isBlank()) {
            return null;
        }
        if ("language".equals(sectionPath)) {
            return metadata;
        }
        if (sectionPath.startsWith("magicutils.")) {
            String key = sectionPath.substring("magicutils.".length());
            Function<LanguageConfig, Object> accessor = SECTION_ACCESSORS.get(key);
            return accessor != null ? accessor.apply(this) : null;
        }
        return null;
    }
}
