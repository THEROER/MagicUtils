package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultSubLoggersProvider;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Configuration for the MagicUtils Logger system.
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
    public String[] getChatGradient() {
        return chat.isAutoGenerateColors() ? null : chat.getGradient().toArray(new String[0]);
    }
    
    public String[] getConsoleGradient() {
        return console.isAutoGenerateColors() ? null : console.getGradient().toArray(new String[0]);
    }
    
    public String[] getChatColors(String type) {
        if (chat.isAutoGenerateColors()) return null;
        List<String> colors = chat.getColors().toMap().get(type);
        return colors != null ? colors.toArray(new String[0]) : null;
    }
    
    public String[] getConsoleColors(String type) {
        if (console.isAutoGenerateColors()) return null;
        List<String> colors = console.getColors().toMap().get(type);
        return colors != null ? colors.toArray(new String[0]) : null;
    }
    
    public boolean isSubLoggerEnabled(String name) {
        SubLoggerConfig config = subLoggers.get(name);
        return config == null || config.isEnabled();
    }
}

