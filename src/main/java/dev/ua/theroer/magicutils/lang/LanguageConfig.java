package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.lang.messages.*;
import dev.ua.theroer.magicutils.lang.providers.EmptyMapProvider;
import java.util.HashMap;
import java.util.Map;

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
    @DefaultValue(provider = EmptyMapProvider.class)
    private Map<String, String> customMessages = new HashMap<>();
    
    // Getters
    /**
     * Gets the language metadata.
     * @return the language metadata instance
     */
    public LanguageMetadata getMetadata() { return metadata; }
    
    /**
     * Gets the command messages configuration.
     * @return the command messages instance
     */
    public CommandMessages getCommands() { return commands; }
    
    /**
     * Gets the settings messages configuration.
     * @return the settings messages instance
     */
    public SettingsMessages getSettings() { return settings; }
    
    /**
     * Gets the reload messages configuration.
     * @return the reload messages instance
     */
    public ReloadMessages getReload() { return reload; }
    
    /**
     * Gets the system messages configuration.
     * @return the system messages instance
     */
    public SystemMessages getSystem() { return system; }
    
    /**
     * Gets the error messages configuration.
     * @return the error messages instance
     */
    public ErrorMessages getErrors() { return errors; }
    
    /**
     * Gets the custom messages map.
     * @return map of custom message keys to values
     */
    public Map<String, String> getCustomMessages() { return customMessages; }
    
    /**
     * Gets a message by its key. First checks custom messages, then internal messages.
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
        
        switch (parts[1]) {
            case "commands": return commands.getMessage(parts[2]);
            case "settings": return settings.getMessage(parts[2]);
            case "reload": return reload.getMessage(parts[2]);
            case "system": return system.getMessage(parts[2]);
            case "errors": return errors.getMessage(parts[2]);
            default: return null;
        }
    }
}