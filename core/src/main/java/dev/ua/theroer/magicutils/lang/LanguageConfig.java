package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.lang.messages.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Language configuration using MagicUtils config system.
 * This class manages language-specific messages for MagicUtils including
 * commands, settings, reload operations, system messages, and errors.
 * 
 * Constructor initializes all message categories with their default values.
 */
@ConfigFile("lang/{lang}.yml")
@Comment("Language file for MagicUtils")
public class LanguageConfig {

    private static final Map<Class<?>, Map<String, Field>> SECTION_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Function<LanguageConfig, Object>> SECTION_ACCESSORS;

    static {
        Map<String, Function<LanguageConfig, Object>> accessors = new HashMap<>();
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

    // Getters
    /**
     * Gets the language metadata.
     * 
     * @return the language metadata instance
     */
    public LanguageMetadata getMetadata() {
        return metadata;
    }

    /**
     * Gets the command messages configuration.
     * 
     * @return the command messages instance
     */
    public CommandMessages getCommands() {
        return commands;
    }

    /**
     * Gets the settings messages configuration.
     * 
     * @return the settings messages instance
     */
    public SettingsMessages getSettings() {
        return settings;
    }

    /**
     * Gets the reload messages configuration.
     * 
     * @return the reload messages instance
     */
    public ReloadMessages getReload() {
        return reload;
    }

    /**
     * Gets the system messages configuration.
     * 
     * @return the system messages instance
     */
    public SystemMessages getSystem() {
        return system;
    }

    /**
     * Gets the error messages configuration.
     * 
     * @return the error messages instance
     */
    public ErrorMessages getErrors() {
        return errors;
    }

    /**
     * Gets the custom messages map.
     * 
     * @return map of custom message keys to values
     */
    public Map<String, String> getCustomMessages() {
        return customMessages;
    }

    /**
     * Gets a message by its key. First checks custom messages, then internal
     * messages.
     * For internal messages, the key format should be 'magicutils.category.key'.
     * 
     * @param key the message key in format 'magicutils.category.key' or custom key
     * @return the message string or null if not found
     */
    public String getMessage(String key) {
        // Check custom messages first
        if (customMessages.containsKey(key)) {
            return customMessages.get(key);
        }

        // Check internal messages
        String[] parts = key.split("\\.", 3);
        if (parts.length < 3 || !parts[0].equals("magicutils")) {
            return null;
        }

        Function<LanguageConfig, Object> accessor = SECTION_ACCESSORS.get(parts[1]);
        if (accessor == null) {
            return null;
        }

        Object section = accessor.apply(this);
        return resolveSectionValue(section, parts[2]);
    }

    private String resolveSectionValue(Object section, String key) {
        if (section == null || key == null) {
            return null;
        }

        Map<String, Field> fieldMap = SECTION_FIELD_CACHE.computeIfAbsent(section.getClass(),
                LanguageConfig::mapSectionFields);
        Field field = fieldMap.get(key);
        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(section);
            return value != null ? value.toString() : null;
        } catch (IllegalAccessException e) {
            return null;
        }
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
}
