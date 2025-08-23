package dev.ua.theroer.magicutils.lang;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Universal language manager for plugins.
 * Supports loading language files from resources and custom files.
 */
public class LanguageManager {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> loadedLanguages = new HashMap<>();
    private String currentLanguage = "en";
    private YamlConfiguration currentConfig;
    private YamlConfiguration fallbackConfig;
    
    /**
     * Creates a new LanguageManager.
     * @param plugin the plugin instance
     */
    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initializes the language manager with default language.
     * @param defaultLanguage the default language code
     */
    public void init(String defaultLanguage) {
        this.currentLanguage = defaultLanguage;
        loadLanguage(currentLanguage);
        
        // Load fallback language (usually English)
        if (!currentLanguage.equals("en")) {
            loadFallbackLanguage("en");
        }
    }
    
    /**
     * Loads a language file.
     * @param languageCode the language code (e.g., "en", "uk", "es")
     * @return true if loaded successfully
     */
    public boolean loadLanguage(String languageCode) {
        try {
            // First try to load from plugin's data folder
            File customFile = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            
            // Load default from resources
            InputStream defaultStream = plugin.getResource("lang/" + languageCode + ".yml");
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );
                
                // Copy defaults
                for (String key : defaultConfig.getKeys(true)) {
                    if (!defaultConfig.isConfigurationSection(key)) {
                        config.set(key, defaultConfig.get(key));
                    }
                }
            }
            
            // Override with custom values if file exists
            if (customFile.exists()) {
                YamlConfiguration customConfig = YamlConfiguration.loadConfiguration(customFile);
                for (String key : customConfig.getKeys(true)) {
                    if (!customConfig.isConfigurationSection(key)) {
                        config.set(key, customConfig.get(key));
                    }
                }
            } else {
                // Save default file for editing
                if (defaultStream != null) {
                    customFile.getParentFile().mkdirs();
                    config.save(customFile);
                }
            }
            
            loadedLanguages.put(languageCode, config);
            
            if (languageCode.equals(currentLanguage)) {
                currentConfig = config;
            }
            
            plugin.getLogger().info("Loaded language: " + languageCode);
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load language: " + languageCode, e);
            return false;
        }
    }
    
    /**
     * Loads fallback language.
     * @param languageCode the fallback language code
     */
    private void loadFallbackLanguage(String languageCode) {
        if (loadLanguage(languageCode)) {
            fallbackConfig = loadedLanguages.get(languageCode);
        }
    }
    
    /**
     * Sets the current language.
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
     * Gets the current language code.
     * @return current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Gets available language codes.
     * @return set of available language codes
     */
    public Set<String> getAvailableLanguages() {
        return loadedLanguages.keySet();
    }
    
    /**
     * Gets a message by key.
     * @param key the message key (supports dot notation)
     * @return the message or key if not found
     */
    public String getMessage(String key) {
        String message = null;
        
        // Try current language first
        if (currentConfig != null) {
            message = currentConfig.getString(key);
        }
        
        // Fall back to fallback language
        if (message == null && fallbackConfig != null) {
            message = fallbackConfig.getString(key);
        }
        
        // Return key if message not found
        return message != null ? message : key;
    }
    
    /**
     * Gets a message with placeholder replacements.
     * @param key the message key
     * @param placeholders map of placeholder -> value
     * @return the formatted message
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return message;
    }
    
    /**
     * Gets a message with varargs placeholder replacements.
     * @param key the message key
     * @param replacements placeholder, value pairs
     * @return the formatted message
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];
            
            // Support both {placeholder} and placeholder format
            message = message.replace("{" + placeholder + "}", value);
            message = message.replace(placeholder, value);
        }
        
        return message;
    }
    
    /**
     * Checks if a message key exists.
     * @param key the message key
     * @return true if key exists
     */
    public boolean hasMessage(String key) {
        if (currentConfig != null && currentConfig.contains(key)) {
            return true;
        }
        
        return fallbackConfig != null && fallbackConfig.contains(key);
    }
    
    /**
     * Reloads all loaded languages.
     */
    public void reload() {
        Set<String> languages = Set.copyOf(loadedLanguages.keySet());
        loadedLanguages.clear();
        
        for (String lang : languages) {
            loadLanguage(lang);
        }
        
        // Restore current language
        if (loadedLanguages.containsKey(currentLanguage)) {
            currentConfig = loadedLanguages.get(currentLanguage);
        }
    }
    
    /**
     * Saves custom messages to file.
     * @param languageCode the language code
     * @param customMessages custom messages to save
     */
    public void saveCustomMessages(String languageCode, Map<String, Object> customMessages) {
        try {
            File customFile = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
            customFile.getParentFile().mkdirs();
            
            YamlConfiguration config = loadedLanguages.getOrDefault(languageCode, new YamlConfiguration());
            
            for (Map.Entry<String, Object> entry : customMessages.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }
            
            config.save(customFile);
            loadedLanguages.put(languageCode, config);
            
            if (languageCode.equals(currentLanguage)) {
                currentConfig = config;
            }
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save custom messages for language: " + languageCode, e);
        }
    }
}