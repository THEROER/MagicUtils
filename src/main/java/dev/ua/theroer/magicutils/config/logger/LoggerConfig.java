package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultSubLoggersProvider;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Configuration for the MagicUtils Logger system.
 * This class manages all logging-related settings including plugin identification,
 * debug options, message formatting for both chat and console, and sub-logger configurations.
 * 
 * Constructor initializes default values for all logging components.
 */
@ConfigFile("logger.yml")
@ConfigReloadable(sections = {"chat", "console", "sub-loggers"})
@Comment("MagicUtils Logger Configuration")
@Getter
public class LoggerConfig {
    
    @ConfigValue("plugin-name")
    @Comment("Plugin name for display (auto-populated on first run)")
    @Setter
    private String pluginName = "";
    
    @ConfigValue("short-name")
    @Comment("Short name for prefixes (auto-generated from plugin name)")
    @Setter
    private String shortName = "";
    
    @ConfigValue("debug-commands")
    @DefaultValue("true")
    @Comment("Whether to show debug messages for command processing")
    private boolean debugCommands;
    
    @ConfigValue("auto-localization")
    @DefaultValue("false")
    @Comment("Whether to automatically localize messages using LanguageManager")
    @Setter
    private boolean autoLocalization;
    
    @ConfigSection("prefix")
    @Comment("Prefix configuration")
    private PrefixSettings prefix = new PrefixSettings();
    
    @ConfigSection("defaults")
    @Comment("Default settings")
    private DefaultSettings defaults = new DefaultSettings();
    
    @ConfigSection("chat")
    @Comment("Chat message formatting settings")
    private ChatSettings chat = new ChatSettings();
    
    @ConfigSection("console")
    @Comment("Console message formatting settings")
    private ConsoleSettings console = new ConsoleSettings();
    
    @ConfigValue("sub-loggers")
    @Comment("Configuration for sub-loggers")
    @DefaultValue(provider = DefaultSubLoggersProvider.class)
    private Map<String, SubLoggerConfig> subLoggers;
    
    // Custom methods for Logger to use
    /**
     * Gets the chat gradient colors array.
     * @return array of gradient colors for chat, or null if auto-generation is enabled
     */
    public String[] getChatGradient() {
        return chat.isAutoGenerateColors() ? null : chat.getGradient().toArray(new String[0]);
    }
    
    /**
     * Gets the console gradient colors array.
     * @return array of gradient colors for console, or null if auto-generation is enabled
     */
    public String[] getConsoleGradient() {
        return console.isAutoGenerateColors() ? null : console.getGradient().toArray(new String[0]);
    }
    
    /**
     * Gets the chat colors for a specific message type.
     * @param type the message type
     * @return array of colors for the specified type, or null if auto-generation is enabled or type not found
     */
    public String[] getChatColors(String type) {
        if (chat.isAutoGenerateColors()) return null;
        List<String> colors = chat.getColors().toMap().get(type);
        return colors != null ? colors.toArray(new String[0]) : null;
    }
    
    /**
     * Gets the console colors for a specific message type.
     * @param type the message type
     * @return array of colors for the specified type, or null if auto-generation is enabled or type not found
     */
    public String[] getConsoleColors(String type) {
        if (console.isAutoGenerateColors()) return null;
        List<String> colors = console.getColors().toMap().get(type);
        return colors != null ? colors.toArray(new String[0]) : null;
    }
    
    /**
     * Checks if a sub-logger is enabled.
     * @param name the sub-logger name
     * @return true if the sub-logger is enabled or not configured (defaults to enabled)
     */
    public boolean isSubLoggerEnabled(String name) {
        SubLoggerConfig config = subLoggers.get(name);
        return config == null || config.isEnabled();
    }
    
    /**
     * Gets the chat prefix mode.
     * @return the prefix mode for chat messages
     */
    public Logger.PrefixMode getChatPrefixMode() {
        try {
            return Logger.PrefixMode.valueOf(prefix.getChatMode());
        } catch (IllegalArgumentException e) {
            return Logger.PrefixMode.FULL; // Default fallback
        }
    }
    
    /**
     * Gets the console prefix mode.
     * @return the prefix mode for console messages
     */
    public Logger.PrefixMode getConsolePrefixMode() {
        try {
            return Logger.PrefixMode.valueOf(prefix.getConsoleMode());
        } catch (IllegalArgumentException e) {
            return Logger.PrefixMode.SHORT; // Default fallback
        }
    }
    
    /**
     * Gets the default target.
     * @return the default target for messages
     */
    public Logger.Target getDefaultTarget() {
        try {
            return Logger.Target.valueOf(defaults.getTarget());
        } catch (IllegalArgumentException e) {
            return Logger.Target.BOTH; // Default fallback
        }
    }
}

