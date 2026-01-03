package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-agnostic language manager.
 */
public class LanguageManager {
    private final Platform platform;
    private final ConfigManager configManager;
    private final PlatformLogger logger;
    private final Map<String, LanguageConfig> loadedLanguages = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private final Set<String> loggedMissingMessages = ConcurrentHashMap.newKeySet();
    private static volatile ObjectMapper yamlMapper;
    @Getter
    private String currentLanguage = "en";
    private LanguageConfig currentConfig;
    private LanguageConfig fallbackConfig;
    private String fallbackLanguage = "en";
    @Getter @Setter
    private boolean logMissingMessages = false;

    /**
     * Create a language manager, resolving a {@link Platform} from the provided platform or legacy plugin instance.
     *
     * @param platformOrPlugin platform abstraction or Bukkit plugin
     * @param configManager config manager for loading language files
     */
    public LanguageManager(Object platformOrPlugin, ConfigManager configManager) {
        this(resolvePlatform(platformOrPlugin), configManager);
    }

    /**
     * Create a language manager bound to a specific platform.
     *
     * @param platform platform abstraction
     * @param configManager config manager for loading language files
     */
    public LanguageManager(Platform platform, ConfigManager configManager) {
        this.platform = platform;
        this.configManager = configManager;
        this.logger = platform.logger();
    }

    /**
     * Initialise language manager and load default language (plus fallback if different).
     *
     * @param defaultLanguage language code to load first
     */
    public void init(String defaultLanguage) {
        this.currentLanguage = defaultLanguage;
        this.loggedMissingMessages.clear();
        loadLanguage(currentLanguage);

        if (!currentLanguage.equals(fallbackLanguage)) {
            loadFallbackLanguage(fallbackLanguage);
        } else {
            fallbackConfig = currentConfig;
        }
    }

    /**
     * Load a language file into memory.
     *
     * @param languageCode language code to load
     * @return true if successfully loaded
     */
    public boolean loadLanguage(String languageCode) {
        try {
            this.loggedMissingMessages.clear();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("lang", languageCode);

            LanguageConfig config = configManager.register(LanguageConfig.class, placeholders);
            initializeLanguageDefaults(config, languageCode);

            loadedLanguages.put(languageCode, config);

            try {
                configManager.save(config);
            } catch (Exception e) {
                logger.warn("Failed to persist language file for: " + languageCode, e);
            }

            if (languageCode.equals(currentLanguage)) {
                currentConfig = config;
            }

            if (languageCode.equals(fallbackLanguage)) {
                fallbackConfig = config;
            }

            logger.info("Loaded language: " + languageCode);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to load language: " + languageCode, e);
            return false;
        }
    }

    private void initializeLanguageDefaults(LanguageConfig config, String languageCode) {
        Map<String, Map<String, String>> translations = createTranslations(languageCode);
        if (!translations.isEmpty()) {
            saveLanguageTranslations(languageCode, translations);
            try {
                configManager.reload(config);
            } catch (Exception e) {
                logger.warn("Failed to reload language config after setting translations", e);
            }
        }
    }

    private void loadFallbackLanguage(String languageCode) {
        if (loadLanguage(languageCode)) {
            this.fallbackLanguage = languageCode;
            fallbackConfig = loadedLanguages.get(languageCode);
        }
    }

    /**
     * Switch the current language for the manager.
     *
     * @param languageCode language code to set
     * @return true if language is now active
     */
    public boolean setLanguage(String languageCode) {
        if (!loadedLanguages.containsKey(languageCode)) {
            if (!loadLanguage(languageCode)) {
                return false;
            }
        }

        this.currentLanguage = languageCode;
        this.currentConfig = loadedLanguages.get(languageCode);
        return true;
    }

    /**
     * Add or update a custom message for a specific language (or current language when null).
     * Saves the language file immediately.
     *
     * @param languageCode target language or null for current
     * @param key message key
     * @param value message text (null removes the key)
     */
    public void putCustomMessage(String languageCode, String key, String value) {
        String code = languageCode != null ? languageCode : currentLanguage;
        LanguageConfig cfg = loadedLanguages.get(code);
        if (cfg == null) {
            return;
        }
        cfg.putCustomMessage(key, value);
        try {
            configManager.save(cfg);
        } catch (Exception e) {
            logger.warn("Failed to save custom message '" + key + "' for language " + code, e);
        }
    }

    /**
     * Set fallback language used when a key is missing in the current language.
     *
     * @param languageCode fallback language code
     */
    public void setFallbackLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return;
        }

        this.fallbackLanguage = languageCode;
        if (!languageCode.equals(currentLanguage)) {
            loadFallbackLanguage(languageCode);
        } else {
            fallbackConfig = currentConfig;
        }
    }

    /**
     * List languages available on disk or already loaded.
     *
     * @return set of language codes found on disk or loaded in memory
     */
    public Set<String> getAvailableLanguages() {
        Set<String> languages = new HashSet<>();
        File langDir = platform.configDir().resolve("lang").toFile();

        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    languages.add(name.substring(0, name.length() - 4));
                }
            }
        }

        languages.addAll(loadedLanguages.keySet());
        return languages;
    }

    /**
     * Get metadata for every available language.
     *
     * @return metadata for each available language
     */
    public Map<String, LanguageInfo> getLanguageInfos() {
        Map<String, LanguageInfo> infos = new HashMap<>();

        for (String code : getAvailableLanguages()) {
            if (!loadedLanguages.containsKey(code)) {
                loadLanguage(code);
            }

            LanguageConfig config = loadedLanguages.get(code);
            if (config != null) {
                infos.put(code, new LanguageInfo(
                        code,
                        config.getMetadata().getName(),
                        config.getMetadata().getAuthor(),
                        config.getMetadata().getVersion()));
            }
        }

        return infos;
    }

    /**
     * Resolve a message in the current language.
     *
     * @param key message key
     * @return resolved message or key if missing
     */
    public String getMessage(String key) {
        return resolveMessage(currentLanguage, key);
    }

    /**
     * Resolve a message with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return message with substitutions applied
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = resolveMessage(currentLanguage, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolve a message with positional replacements (name/value pairs).
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return message with substitutions applied
     */
    public String getMessage(String key, String... replacements) {
        String message = resolveMessage(currentLanguage, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Check whether the current language (or fallback) has a message for the key.
     *
     * @param key message key
     * @return true if present
     */
    public boolean hasMessage(String key) {
        if (hasMessage(currentConfig, key)) {
            return true;
        }
        ensureFallbackLoaded();
        return hasMessage(fallbackConfig, key);
    }

    /**
     * Check whether a specific language (or fallback) has a message for the key.
     *
     * @param languageCode language to inspect
     * @param key message key
     * @return true if present
     */
    public boolean hasMessageForLanguage(String languageCode, String key) {
        LanguageConfig primary = getOrLoadLanguage(languageCode);
        if (hasMessage(primary, key)) {
            return true;
        }
        ensureFallbackLoaded();
        return hasMessage(fallbackConfig, key);
    }

    /**
     * Resolve a message for a specific language code.
     *
     * @param languageCode language to use
     * @param key message key
     * @return resolved message or key if missing
     */
    public String getMessageForLanguage(String languageCode, String key) {
        return resolveMessage(languageCode, key);
    }

    /**
     * Resolve a message with placeholders for a specific language code.
     *
     * @param languageCode language to use
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message
     */
    public String getMessageForLanguage(String languageCode, String key, Map<String, String> placeholders) {
        String message = resolveMessage(languageCode, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolve a message with positional replacements for a specific language code.
     *
     * @param languageCode language to use
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message
     */
    public String getMessageForLanguage(String languageCode, String key, String... replacements) {
        String message = resolveMessage(languageCode, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Assign a preferred language for a player.
     *
     * @param playerId player UUID
     * @param languageCode language code to set (null/empty clears preference)
     * @return true if updated
     */
    public boolean setPlayerLanguage(UUID playerId, String languageCode) {
        if (playerId == null) {
            return false;
        }

        if (languageCode == null || languageCode.isEmpty()) {
            playerLanguages.remove(playerId);
            return true;
        }

        if (!loadedLanguages.containsKey(languageCode) && !loadLanguage(languageCode)) {
            return false;
        }

        playerLanguages.put(playerId, languageCode);
        return true;
    }

    /**
     * Assign a preferred language for an {@link Audience}.
     *
     * @param audience audience with id
     * @param languageCode language code to set
     * @return true if updated
     */
    public boolean setPlayerLanguage(Audience audience, String languageCode) {
        return audience != null && setPlayerLanguage(audience.id(), languageCode);
    }

    /**
     * Assign a preferred language for a player-like object (expects getUniqueId()).
     *
     * @param player player object
     * @param languageCode language code to set
     * @return true if updated
     */
    public boolean setPlayerLanguage(Object player, String languageCode) {
        UUID id = extractUuid(player);
        return setPlayerLanguage(id, languageCode);
    }

    /**
     * Remove stored language preference for a player.
     *
     * @param playerId player UUID
     */
    public void clearPlayerLanguage(UUID playerId) {
        if (playerId != null) {
            playerLanguages.remove(playerId);
        }
    }

    /**
     * Remove stored language preference for an arbitrary player object.
     *
     * @param player player object
     */
    public void clearPlayerLanguage(Object player) {
        UUID id = extractUuid(player);
        clearPlayerLanguage(id);
    }

    /**
     * Get stored language for a player or current language if none.
     *
     * @param playerId player UUID
     * @return language code
     */
    public String getPlayerLanguage(UUID playerId) {
        if (playerId == null) {
            return currentLanguage;
        }
        return playerLanguages.getOrDefault(playerId, currentLanguage);
    }

    /**
     * Get stored language for a player-like object.
     *
     * @param player player object
     * @return language code
     */
    public String getPlayerLanguage(Object player) {
        UUID id = extractUuid(player);
        return id != null ? getPlayerLanguage(id) : currentLanguage;
    }

    /**
     * Snapshot of stored player language preferences.
     *
     * @return snapshot of player language preferences
     */
    public Map<UUID, String> getPlayerLanguages() {
        return Collections.unmodifiableMap(new HashMap<>(playerLanguages));
    }

    /**
     * Resolve a message for an audience respecting per-player language overrides.
     *
     * @param audience target audience
     * @param key message key
     * @return resolved message
     */
    public String getMessageForAudience(Audience audience, String key) {
        if (audience == null || audience.id() == null) {
            return getMessage(key);
        }
        return resolveMessage(getPlayerLanguage(audience.id()), key);
    }

    /**
     * Resolve a message for an audience with placeholders.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message
     */
    public String getMessageForAudience(Audience audience, String key, Map<String, String> placeholders) {
        String message = getMessageForAudience(audience, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolve a message for an audience with positional replacements.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message
     */
    public String getMessageForAudience(Audience audience, String key, String... replacements) {
        String message = getMessageForAudience(audience, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Reload all loaded languages from disk.
     */
    public void reload() {
        loggedMissingMessages.clear();
        Map<String, LanguageConfig> snapshot = new HashMap<>(loadedLanguages);

        for (Map.Entry<String, LanguageConfig> entry : snapshot.entrySet()) {
            configManager.reload(entry.getValue());
        }

        currentConfig = loadedLanguages.get(currentLanguage);
        if (currentConfig == null) {
            loadLanguage(currentLanguage);
            currentConfig = loadedLanguages.get(currentLanguage);
        }

        fallbackConfig = loadedLanguages.get(fallbackLanguage);
        if (fallbackConfig == null && fallbackLanguage != null) {
            loadFallbackLanguage(fallbackLanguage);
        }
    }

    /**
     * Persist custom messages into the language file.
     *
     * @param languageCode language to modify
     * @param customMessages map of keys to values to persist
     */
    public void saveCustomMessages(String languageCode, Map<String, String> customMessages) {
        try {
            LanguageConfig config = loadedLanguages.get(languageCode);
            if (config == null) {
                loadLanguage(languageCode);
                config = loadedLanguages.get(languageCode);
            }

            if (config != null) {
                config.getCustomMessages().putAll(customMessages);
                configManager.save(config);
            }

        } catch (Exception e) {
            logger.warn("Failed to save language messages for: " + languageCode, e);
        }
    }

    /**
     * Persist currently loaded MagicUtils messages (ensures defaults exist).
     */
    public void addMagicUtilsMessages() {
        for (LanguageConfig config : loadedLanguages.values()) {
            configManager.save(config);
        }
    }

    private LanguageConfig getOrLoadLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return currentConfig;
        }

        LanguageConfig config = loadedLanguages.get(languageCode);
        if (config == null && loadLanguage(languageCode)) {
            config = loadedLanguages.get(languageCode);
        }
        return config;
    }

    private void ensureFallbackLoaded() {
        if (fallbackConfig == null && fallbackLanguage != null) {
            loadFallbackLanguage(fallbackLanguage);
        }
    }

    private String resolveMessage(String languageCode, String key) {
        LanguageConfig primary = getOrLoadLanguage(languageCode);
        if (primary != null) {
            String message = primary.getMessage(key);
            if (message != null) {
                return message;
            }
        }

        ensureFallbackLoaded();
        if (fallbackConfig != null) {
            String fallbackMessage = fallbackConfig.getMessage(key);
            if (fallbackMessage != null) {
                return fallbackMessage;
            }
        }

        logMissing(languageCode, key);
        return key;
    }

    private boolean hasMessage(LanguageConfig config, String key) {
        if (config == null) return false;
        return config.getMessage(key) != null;
    }

    private void logMissing(String languageCode, String key) {
        if (!logMissingMessages) {
            return;
        }
        String composite = languageCode + "::" + key;
        if (loggedMissingMessages.add(composite)) {
            String fallbackInfo = fallbackLanguage != null ? ("; fallback=" + fallbackLanguage) : "";
            logger.debug("Missing translation for key '" + key + "' in language '" + languageCode + "'" + fallbackInfo);
        }
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String applyReplacements(String message, String... replacements) {
        if (replacements == null || replacements.length == 0) {
            return message;
        }
        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];

            result = result.replace("{" + placeholder + "}", value);
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private Map<String, Map<String, String>> createTranslations(String languageCode) {
        return LanguageDefaults.localizedSections(languageCode);
    }

    private void saveLanguageTranslations(String languageCode, Map<String, Map<String, String>> translations) {
        try {
            File langFile = platform.configDir().resolve("lang/" + languageCode + ".yml").toFile();
            Path parent = langFile.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> data = new HashMap<>();
            if (langFile.exists() && langFile.length() > 0) {
                ObjectMapper mapper = yamlMapper();
                Map<String, Object> loaded = mapper.readValue(langFile, new TypeReference<>() {});
                if (loaded != null) {
                    data.putAll(castMap(loaded));
                }
            }

            for (Map.Entry<String, Map<String, String>> section : translations.entrySet()) {
                for (Map.Entry<String, String> entry : section.getValue().entrySet()) {
                    applyPathIfAbsent(data, section.getKey() + "." + entry.getKey(), entry.getValue());
                }
            }

            ObjectWriter writer = yamlMapper().writerWithDefaultPrettyPrinter();
            writer.writeValue(langFile, data);
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to save language translations for: " + languageCode, e);
        }
    }

    private static ObjectMapper yamlMapper() {
        ObjectMapper local = yamlMapper;
        if (local != null) {
            return local;
        }
        synchronized (LanguageManager.class) {
            local = yamlMapper;
            if (local == null) {
                local = createYamlMapper();
                yamlMapper = local;
            }
        }
        return local;
    }

    private static ObjectMapper createYamlMapper() {
        JsonFactory factory = buildYamlFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private static JsonFactory buildYamlFactory() {
        try {
            Class<?> factoryClass = Class.forName(resolveJacksonClass("com.fasterxml.jackson.dataformat.yaml.YAMLFactory"));
            Object builder = invokeStatic(factoryClass, "builder");
            if (builder != null) {
                disableYamlDocStart(builder);
                Object built = invokeNoArgs(builder, "build");
                if (built instanceof JsonFactory) {
                    return (JsonFactory) built;
                }
            }
            Object factory = factoryClass.getDeclaredConstructor().newInstance();
            return (JsonFactory) factory;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("YAML support failed to initialize. Add magicutils-config-yaml.", e);
        }
    }

    private static Object invokeStatic(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void disableYamlDocStart(Object builder) {
        if (builder == null) {
            return;
        }
        try {
            Class<?> featureClass = Class.forName(resolveJacksonClass("com.fasterxml.jackson.dataformat.yaml.YAMLGenerator$Feature"));
            Object feature = Enum.valueOf((Class<Enum>) featureClass, "WRITE_DOC_START_MARKER");
            builder.getClass().getMethod("disable", featureClass).invoke(builder, feature);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static String resolveJacksonClass(String className) {
        if (className == null) {
            return null;
        }
        String relocated = className.replace("com.fasterxml.jackson.", "dev.ua.theroer.magicutils.libs.jackson.");
        if (!relocated.equals(className) && isClassPresent(relocated)) {
            return relocated;
        }
        return className;
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, LanguageManager.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void applyPathIfAbsent(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object child = current.get(part);
            if (!(child instanceof Map)) {
                child = new HashMap<String, Object>();
                current.put(part, child);
            }
            current = (Map<String, Object>) child;
        }
        String leaf = parts[parts.length - 1];
        Object existing = current.get(leaf);
        if (existing == null) {
            current.put(leaf, value);
        }
    }

    private UUID extractUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Audience audience) {
            return audience.id();
        }
        try {
            var m = obj.getClass().getMethod("getUniqueId");
            Object res = m.invoke(obj);
            if (res instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
        return null;
    }

    private static Platform resolvePlatform(Object platformOrPlugin) {
        if (platformOrPlugin instanceof Platform) {
            return (Platform) platformOrPlugin;
        }
        if (platformOrPlugin != null) {
            try {
                Class<?> providerClass = Class
                        .forName("dev.ua.theroer.magicutils.platform.bukkit.BukkitPlatformProvider");
                for (var ctor : providerClass.getConstructors()) {
                    if (ctor.getParameterCount() == 1
                            && ctor.getParameterTypes()[0].isAssignableFrom(platformOrPlugin.getClass())) {
                        Object instance = ctor.newInstance(platformOrPlugin);
                        if (instance instanceof Platform) {
                            return (Platform) instance;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalArgumentException("Platform provider is required to initialize LanguageManager");
    }
}
