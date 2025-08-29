package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Type parser for language key suggestions.
 */
public class LanguageKeyTypeParser implements TypeParser<String> {
    private static LanguageManager languageManager;
    private static JavaPlugin plugin;
    
    /**
     * Sets the language manager for this parser.
     * @param manager the language manager
     */
    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
    }
    
    /**
     * Sets the plugin for this parser.
     * @param p the plugin
     */
    public static void setPlugin(JavaPlugin p) {
        plugin = p;
    }
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        // This parser is only for suggestions
        return false;
    }
    
    @Override
    @Nullable
    public String parse(@Nullable String value, @NotNull Class<String> targetType, @NotNull CommandSender sender) {
        // This parser doesn't parse values
        return null;
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        return getKeysFromFiles(null);
    }
    
    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "@language_keys".equals(source);
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
    
    private List<String> getKeysFromFiles(String languageCode) {
        Set<String> allKeys = new HashSet<>();
        
        try {
            if (plugin != null) {
                File langDir = new File(plugin.getDataFolder(), "lang");
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
                }
            }
        } catch (Exception e) {
            // Return empty list on any error
        }
        
        return new ArrayList<>(allKeys);
    }
    
    private void addAllKeys(YamlConfiguration config, String prefix, Set<String> allKeys) {
        for (String key : config.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (config.isConfigurationSection(key)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null) {
                    addAllKeysFromSection(section, fullKey, allKeys);
                }
            } else {
                allKeys.add(fullKey);
            }
        }
    }
    
    private void addAllKeysFromSection(ConfigurationSection section, String prefix, Set<String> allKeys) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            
            if (section.isConfigurationSection(key)) {
                ConfigurationSection nestedSection = section.getConfigurationSection(key);
                if (nestedSection != null) {
                    addAllKeysFromSection(nestedSection, fullKey, allKeys);
                }
            } else {
                allKeys.add(fullKey);
            }
        }
    }
}