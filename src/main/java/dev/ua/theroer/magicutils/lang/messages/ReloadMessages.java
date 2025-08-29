package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;

/**
 * Reload messages
 */
public class ReloadMessages {
    @ConfigValue("all_commands")
    @DefaultValue("&aAll commands reloaded!")
    private String allCommands;
    
    @ConfigValue("command")
    @DefaultValue("&aCommand &f{command} &areloaded!")
    private String command;
    
    @ConfigValue("all_sections")
    @DefaultValue("&aAll sections reloaded!")
    private String allSections;
    
    @ConfigValue("section")
    @DefaultValue("&aSection &f{section} &areloaded!")
    private String section;
    
    @ConfigValue("global_settings")
    @DefaultValue("&aGlobal settings reloaded!")
    private String globalSettings;
    
    @ConfigValue("global_setting")
    @DefaultValue("&aGlobal setting &f{setting} &areloaded!")
    private String globalSetting;
    
    public String getMessage(String key) {
        switch (key) {
            case "all_commands": return allCommands;
            case "command": return command;
            case "all_sections": return allSections;
            case "section": return section;
            case "global_settings": return globalSettings;
            case "global_setting": return globalSetting;
            default: return null;
        }
    }
}