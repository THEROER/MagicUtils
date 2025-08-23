package dev.ua.theroer.magicutils.commands.parsers.suggestion;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.SuggestionParser;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parser for language key suggestions from YAML files.
 */
public class LanguageKeySuggestionParser implements SuggestionParser {
    
    @Setter
    private static LanguageManager languageManager;
    
    @Setter
    private static JavaPlugin plugin;
    
    /**
     * Creates a new LanguageKeySuggestionParser.
     */
    public LanguageKeySuggestionParser() {
        // Default constructor
    }

    @Override
    public boolean canParse(@NotNull String source) {
        return source.equals("@language_keys") || source.startsWith("@language_keys:");
    }

    @Override
    @NotNull
    public List<String> parse(@NotNull String source, @NotNull CommandSender sender) {
        if (languageManager == null) {
            return new ArrayList<>();
        }
        
        String targetLang = null;
        if (source.startsWith("@language_keys:")) {
            targetLang = source.substring("@language_keys:".length());
        }
        
        return getLanguageKeys(targetLang);
    }

    @Override
    public int getPriority() {
        return 80; // High priority for language keys
    }
    
    /**
     * Gets all available keys from language files.
     * @param languageCode specific language code, or null for current language
     * @return list of all keys
     */
    private List<String> getLanguageKeys(String languageCode) {
        // Always use direct file reading for dynamic key extraction
        return getKeysFromFiles(languageCode);
    }
    
    /**
     * Adds keys from specific language using LanguageManager.
     * @param allKeys set to add keys to
     * @param languageCode language code
     */
    private void addKeysFromLanguage(Set<String> allKeys, String languageCode) {
        // This is a workaround since LanguageManager doesn't expose getAllKeys method
        // We'll try to read the file directly
        File langFile = new File("lang/" + languageCode + ".yml");
        if (langFile.exists()) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                addAllKeys(config, "", allKeys);
            } catch (Exception e) {
                // Ignore file read errors
            }
        }
    }
    
    /**
     * Recursively adds all keys from YAML configuration.
     * @param config YAML configuration
     * @param prefix current key prefix
     * @param allKeys set to add keys to
     */
    private void addAllKeys(YamlConfiguration config, String prefix, Set<String> allKeys) {
        for (String key : config.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (config.isConfigurationSection(key)) {
                // Recursively process sections - use ConfigurationSection, not YamlConfiguration
                var section = config.getConfigurationSection(key);
                if (section != null) {
                    addAllKeysFromSection(section, fullKey, allKeys);
                }
            } else {
                // Add leaf key
                allKeys.add(fullKey);
            }
        }
    }
    
    /**
     * Recursively adds all keys from configuration section.
     * @param section configuration section
     * @param prefix current key prefix
     * @param allKeys set to add keys to
     */
    private void addAllKeysFromSection(org.bukkit.configuration.ConfigurationSection section, String prefix, Set<String> allKeys) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (section.isConfigurationSection(key)) {
                // Recursively process nested sections
                var nestedSection = section.getConfigurationSection(key);
                if (nestedSection != null) {
                    addAllKeysFromSection(nestedSection, fullKey, allKeys);
                }
            } else {
                // Add leaf key
                allKeys.add(fullKey);
            }
        }
    }
    
    /**
     * Gets keys directly from language files.
     * @param languageCode specific language or null for all
     * @return list of keys
     */
    private List<String> getKeysFromFiles(String languageCode) {
        Set<String> allKeys = new HashSet<>();
        
        try {
            // First try to use plugin's data folder if available
            if (plugin != null) {
                File langDir = new File(plugin.getDataFolder(), "lang");
                Logger.debug("Checking lang directory: " + langDir.getAbsolutePath());
                Logger.debug("Directory exists: " + langDir.exists());
                if (langDir.exists()) {
                    File[] langFiles;
                    if (languageCode != null) {
                        langFiles = new File[]{new File(langDir, languageCode + ".yml")};
                    } else {
                        langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
                    }
                    
                    if (langFiles != null) {
                        Logger.debug("Found " + langFiles.length + " potential lang files");
                        for (File file : langFiles) {
                            Logger.debug("Checking file: " + file.getAbsolutePath() + ", exists: " + file.exists());
                            if (file.exists() && file.isFile()) {
                                try {
                                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                                    addAllKeys(config, "", allKeys);
                                    Logger.debug("Added keys from file: " + file.getName() + ", total keys now: " + allKeys.size());
                                } catch (Exception e) {
                                    Logger.debug("Error loading file " + file.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    // If we found files, return results
                    if (!allKeys.isEmpty()) {
                        Logger.debug("Returning " + allKeys.size() + " keys from plugin data folder");
                        return new ArrayList<>(allKeys);
                    }
                }
            }
            
            // Fallback to relative paths
            String[] possiblePaths = {
                "lang/",
                "src/main/resources/lang/",
                "resources/lang/"
            };
            
            for (String basePath : possiblePaths) {
                File langDir = new File(basePath);
                if (langDir.exists()) {
                    File[] langFiles;
                    if (languageCode != null) {
                        langFiles = new File[]{new File(langDir, languageCode + ".yml")};
                    } else {
                        langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
                    }
                    
                    if (langFiles != null) {
                        for (File file : langFiles) {
                            if (file.exists() && file.isFile()) {
                                try {
                                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                                    addAllKeys(config, "", allKeys);
                                } catch (Exception e) {
                                    // Ignore individual file errors
                                }
                            }
                        }
                    }
                    
                    // If we found files in this directory, stop searching
                    if (!allKeys.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Return empty list on any error
        }
        
        return new ArrayList<>(allKeys);
    }
}