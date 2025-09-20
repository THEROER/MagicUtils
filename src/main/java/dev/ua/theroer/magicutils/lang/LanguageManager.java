package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Universal language manager for plugins.
 * Supports loading language files from resources and custom files.
 */
public class LanguageManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, LanguageConfig> loadedLanguages = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private String currentLanguage = "en";
    private LanguageConfig currentConfig;
    private LanguageConfig fallbackConfig;
    private String fallbackLanguage = "en";

    /**
     * Creates a new LanguageManager with ConfigManager.
     * 
     * @param plugin        the plugin instance
     * @param configManager the config manager
     */
    public LanguageManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Initializes the language manager with default language.
     * 
     * @param defaultLanguage the default language code
     */
    public void init(String defaultLanguage) {
        this.currentLanguage = defaultLanguage;
        loadLanguage(currentLanguage);

        // Load fallback language (usually English)
        if (!currentLanguage.equals(fallbackLanguage)) {
            loadFallbackLanguage(fallbackLanguage);
        } else {
            fallbackConfig = currentConfig;
        }
    }

    /**
     * Loads a language file.
     * 
     * @param languageCode the language code (e.g., "en", "uk", "es")
     * @return true if loaded successfully
     */
    public boolean loadLanguage(String languageCode) {
        try {
            // Create placeholders for the file path
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("lang", languageCode);

            // Register with ConfigManager which will handle file creation and loading
            LanguageConfig config = configManager.register(LanguageConfig.class, placeholders);

            // Initialize language-specific defaults
            initializeLanguageDefaults(config, languageCode);

            loadedLanguages.put(languageCode, config);

            if (languageCode.equals(currentLanguage)) {
                currentConfig = config;
            }

            if (languageCode.equals(fallbackLanguage)) {
                fallbackConfig = config;
            }

            plugin.getLogger().info(InternalMessages.SYS_LOADED_LANGUAGE.get("language", languageCode));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    InternalMessages.SYS_FAILED_LOAD_LANGUAGE.get("language", languageCode), e);
            return false;
        }
    }

    /**
     * Initializes language-specific defaults.
     */
    private void initializeLanguageDefaults(LanguageConfig config, String languageCode) {
        // Check if this is a newly created file by checking if metadata is still
        // default
        boolean isNewFile = config.getMetadata().getCode().equals("en") && !languageCode.equals("en");

        if (isNewFile) {
            // Create language-specific translations
            Map<String, Map<String, String>> translations = createTranslations(languageCode);

            if (!translations.isEmpty()) {
                // We need to save the config with proper translations
                // Since we can't modify private fields, we'll use a different approach
                // We'll save directly to the file after ConfigManager creates it
                saveLanguageTranslations(languageCode, translations);

                // Reload the config to pick up the new translations
                try {
                    configManager.reload(config);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to reload language config after setting translations",
                            e);
                }
            }
        }
    }

    /**
     * Loads fallback language.
     * 
     * @param languageCode the fallback language code
     */
    private void loadFallbackLanguage(String languageCode) {
        if (loadLanguage(languageCode)) {
            this.fallbackLanguage = languageCode;
            fallbackConfig = loadedLanguages.get(languageCode);
        }
    }

    /**
     * Sets the current language.
     * 
     * @param languageCode the language code
     * @return true if language was loaded successfully
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
     * Sets fallback language that will be used when message is missing in primary language.
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
     * Gets the current language code.
     * 
     * @return current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Gets available language codes by scanning the lang directory.
     * 
     * @return set of available language codes
     */
    public Set<String> getAvailableLanguages() {
        Set<String> languages = new HashSet<>();
        File langDir = new File(plugin.getDataFolder(), "lang");

        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    languages.add(name.substring(0, name.length() - 4));
                }
            }
        }

        // Add loaded languages too
        languages.addAll(loadedLanguages.keySet());

        return languages;
    }

    /**
     * Gets information about available languages.
     * 
     * @return map of language code to language info
     */
    public Map<String, LanguageInfo> getLanguageInfos() {
        Map<String, LanguageInfo> infos = new HashMap<>();

        for (String code : getAvailableLanguages()) {
            if (!loadedLanguages.containsKey(code)) {
                // Load temporarily to get info
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
     * Gets a message by key.
     * 
     * @param key the message key (supports dot notation)
     * @return the message or key if not found
     */
    public String getMessage(String key) {
        return resolveMessage(currentLanguage, key);
    }

    /**
     * Gets a message with placeholder replacements.
     * 
     * @param key          the message key
     * @param placeholders map of placeholder -> value
     * @return the formatted message
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = resolveMessage(currentLanguage, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Gets a message with varargs placeholder replacements.
     * 
     * @param key          the message key
     * @param replacements placeholder, value pairs
     * @return the formatted message
     */
    public String getMessage(String key, String... replacements) {
        String message = resolveMessage(currentLanguage, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Checks if a message key exists.
     * 
     * @param key the message key
     * @return true if key exists
     */
    public boolean hasMessage(String key) {
        if (hasMessage(currentConfig, key)) {
            return true;
        }

        ensureFallbackLoaded();
        return hasMessage(fallbackConfig, key);
    }

    /**
     * Checks message availability for a particular language.
     *
     * @param languageCode target language code
     * @param key          message key
     * @return true if message exists or fallback can serve it
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
     * Gets message for a specific language.
     *
     * @param languageCode the language code to get message for
     * @param key the message key
     * @return the resolved message for the specified language
     */
    public String getMessageForLanguage(String languageCode, String key) {
        return resolveMessage(languageCode, key);
    }

    /**
     * Gets message for a specific language with map placeholders.
     *
     * @param languageCode the language code to get message for
     * @param key the message key
     * @param placeholders map of placeholder keys and values
     * @return the resolved message with placeholders applied
     */
    public String getMessageForLanguage(String languageCode, String key, Map<String, String> placeholders) {
        String message = resolveMessage(languageCode, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Gets message for a specific language with positional replacements.
     *
     * @param languageCode the language code to get message for
     * @param key the message key
     * @param replacements positional replacement values
     * @return the resolved message with replacements applied
     */
    public String getMessageForLanguage(String languageCode, String key, String... replacements) {
        String message = resolveMessage(languageCode, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Sets custom language for player.
     *
     * @param playerId the player's unique identifier
     * @param languageCode the language code to set for the player
     * @return true if the language was set successfully, false otherwise
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
     * Sets custom language for player.
     *
     * @param player the player instance
     * @param languageCode the language code to set for the player
     * @return true if the language was set successfully, false otherwise
     */
    public boolean setPlayerLanguage(Player player, String languageCode) {
        return player != null && setPlayerLanguage(player.getUniqueId(), languageCode);
    }

    /**
     * Clears player specific language.
     *
     * @param playerId the player's unique identifier
     */
    public void clearPlayerLanguage(UUID playerId) {
        if (playerId != null) {
            playerLanguages.remove(playerId);
        }
    }

    /**
     * Clears player specific language.
     *
     * @param player the player instance
     */
    public void clearPlayerLanguage(Player player) {
        if (player != null) {
            clearPlayerLanguage(player.getUniqueId());
        }
    }

    /**
     * Gets player specific language or default one.
     *
     * @param playerId the player's unique identifier
     * @return the player's language code or default language if not set
     */
    public String getPlayerLanguage(UUID playerId) {
        if (playerId == null) {
            return currentLanguage;
        }
        return playerLanguages.getOrDefault(playerId, currentLanguage);
    }

    /**
     * Gets player specific language or default one.
     *
     * @param player the player instance
     * @return the player's language code or default language if not set
     */
    public String getPlayerLanguage(Player player) {
        return player == null ? currentLanguage : getPlayerLanguage(player.getUniqueId());
    }

    /**
     * Returns snapshot of player language assignments.
     *
     * @return unmodifiable map of player IDs to their language codes
     */
    public Map<UUID, String> getPlayerLanguages() {
        return Collections.unmodifiableMap(new HashMap<>(playerLanguages));
    }

    /**
     * Resolves message for player.
     *
     * @param playerId the player's unique identifier
     * @param key the message key
     * @return the resolved message for the player
     */
    public String getMessageForPlayer(UUID playerId, String key) {
        return resolveMessage(getPlayerLanguage(playerId), key);
    }

    /**
     * Resolves message for player with map placeholders.
     *
     * @param playerId the player's unique identifier
     * @param key the message key
     * @param placeholders map of placeholder keys and values
     * @return the resolved message with placeholders applied
     */
    public String getMessageForPlayer(UUID playerId, String key, Map<String, String> placeholders) {
        String message = resolveMessage(getPlayerLanguage(playerId), key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolves message for player with positional replacements.
     *
     * @param playerId the player's unique identifier
     * @param key the message key
     * @param replacements positional replacement values
     * @return the resolved message with replacements applied
     */
    public String getMessageForPlayer(UUID playerId, String key, String... replacements) {
        String message = resolveMessage(getPlayerLanguage(playerId), key);
        return applyReplacements(message, replacements);
    }

    /**
     * Resolves message for player.
     *
     * @param player the player instance
     * @param key the message key
     * @return the resolved message for the player
     */
    public String getMessageForPlayer(Player player, String key) {
        return getMessageForPlayer(player != null ? player.getUniqueId() : null, key);
    }

    /**
     * Resolves message for player with map placeholders.
     *
     * @param player the player instance
     * @param key the message key
     * @param placeholders map of placeholder keys and values
     * @return the resolved message with placeholders applied
     */
    public String getMessageForPlayer(Player player, String key, Map<String, String> placeholders) {
        return getMessageForPlayer(player != null ? player.getUniqueId() : null, key, placeholders);
    }

    /**
     * Resolves message for player with positional replacements.
     *
     * @param player the player instance
     * @param key the message key
     * @param replacements positional replacement values
     * @return the resolved message with replacements applied
     */
    public String getMessageForPlayer(Player player, String key, String... replacements) {
        return getMessageForPlayer(player != null ? player.getUniqueId() : null, key, replacements);
    }

    /**
     * Resolves message for command sender.
     *
     * @param sender the command sender
     * @param key the message key
     * @return the resolved message for the sender
     */
    public String getMessage(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            return getMessageForPlayer(player, key);
        }
        return getMessage(key);
    }

    /**
     * Resolves message for command sender with map placeholders.
     *
     * @param sender the command sender
     * @param key the message key
     * @param placeholders map of placeholder keys and values
     * @return the resolved message with placeholders applied
     */
    public String getMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            return getMessageForPlayer(player, key, placeholders);
        }
        return getMessage(key, placeholders);
    }

    /**
     * Resolves message for command sender with positional replacements.
     *
     * @param sender the command sender
     * @param key the message key
     * @param replacements positional replacement values
     * @return the resolved message with replacements applied
     */
    public String getMessage(CommandSender sender, String key, String... replacements) {
        if (sender instanceof Player player) {
            return getMessageForPlayer(player, key, replacements);
        }
        return getMessage(key, replacements);
    }

    /**
     * Reloads all loaded languages.
     */
    public void reload() {
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
     * Saves custom messages to file.
     * 
     * @param languageCode   the language code
     * @param customMessages custom messages to save
     */
    public void saveCustomMessages(String languageCode, Map<String, String> customMessages) {
        try {
            LanguageConfig config = loadedLanguages.get(languageCode);
            if (config == null) {
                // Load or create new language config
                loadLanguage(languageCode);
                config = loadedLanguages.get(languageCode);
            }

            if (config != null) {
                config.getCustomMessages().putAll(customMessages);
                configManager.save(config);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    InternalMessages.SYS_FAILED_SAVE_MESSAGES.get("language", languageCode), e);
        }
    }

    /**
     * Adds internal MagicUtils messages to the language
     */
    public void addMagicUtilsMessages() {
        // This will be called by MagicUtils to register its internal messages
        for (LanguageConfig config : loadedLanguages.values()) {
            // The LanguageConfig already has all MagicUtils messages as defaults
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
        return resolveMessage(getOrLoadLanguage(languageCode), key);
    }

    private String resolveMessage(LanguageConfig primary, String key) {
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

        return key;
    }

    private boolean hasMessage(LanguageConfig config, String key) {
        return config != null && config.getMessage(key) != null;
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

    /**
     * Creates language-specific translations.
     */
    private Map<String, Map<String, String>> createTranslations(String languageCode) {
        return LanguageDefaults.localizedSections(languageCode);
    }

    /**
     * Saves language translations to file.
     */
    private void saveLanguageTranslations(String languageCode, Map<String, Map<String, String>> translations) {
        try {
            File langFile = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(langFile);

            // Set all translations
            for (Map.Entry<String, Map<String, String>> section : translations.entrySet()) {
                for (Map.Entry<String, String> entry : section.getValue().entrySet()) {
                    yaml.set(section.getKey() + "." + entry.getKey(), entry.getValue());
                }
            }

            yaml.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save language translations for: " + languageCode, e);
        }
    }
}
