package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.utils.ColorUtils;
import dev.ua.theroer.magicutils.logger.PrefixMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the MagicUtils Logger system.
 * This class manages all logging-related settings including plugin
 * identification,
 * debug options, message formatting for both chat and console, and sub-logger
 * configurations.
 * 
 * Constructor initializes default values for all logging components.
 */
@ConfigFile("logger.yml")
@ConfigReloadable(sections = { "chat", "console", "sub-loggers" })
@Comment("MagicUtils Logger Configuration")
@Getter
public class LoggerConfig {
    private static final String[] DEFAULT_GRADIENT = new String[] { "#ffffff", "#ffffff" };
    private static final String[] DEFAULT_ALERT = new String[] { "#ff5555", "#ff5555" };
    private static final String[] DEFAULT_WARN = new String[] { "#ffaa00", "#ffaa00" };
    private static final String[] DEFAULT_DEBUG = new String[] { "#55aaff", "#55aaff" };
    private static final String[] DEFAULT_SUCCESS = new String[] { "#55ff55", "#55ff55" };

    /**
     * Default constructor for LoggerConfig.
     */
    public LoggerConfig() {
    }

    @ConfigValue("plugin-name")
    @Comment("Plugin name for display (auto-populated on first run)")
    @Setter
    private String pluginName = "";

    @ConfigValue("short-name")
    @Comment("Short name for prefixes (auto-generated from plugin name)")
    @Setter
    private String shortName = "";

    @ConfigValue("debug-commands")
    @Comment("Whether to show debug messages for command processing")
    private boolean debugCommands = false;

    @ConfigValue("debug-placeholders")
    @Comment("Whether to log placeholder resolution details")
    private boolean debugPlaceholders = false;

    @ConfigValue("auto-localization")
    @Comment("Whether to automatically localize messages using LanguageManager")
    @Setter
    private boolean autoLocalization = true;

    @ConfigSection("prefix")
    @Comment("Prefix configuration")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PrefixSettings prefix = new PrefixSettings();

    @ConfigSection("defaults")
    @Comment("Default settings")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private DefaultSettings defaults = new DefaultSettings();

    @ConfigSection("chat")
    @Comment("Chat message formatting settings")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ChatSettings chat = new ChatSettings();

    @ConfigSection("console")
    @Comment("Console message formatting settings")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ConsoleSettings console = new ConsoleSettings();

    @ConfigValue("sub-loggers")
    @Comment("Configuration for sub-loggers")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, SubLoggerConfig> subLoggers = new LinkedHashMap<>();

    // Custom methods for Logger to use
    /**
     * Gets the chat gradient colors array.
     * 
     * @return array of gradient colors for chat, or null if auto-generation is
     *         enabled
     */
    public String[] getChatGradient() {
        return chat.isAutoGenerateColors() ? null : chat.getGradient().toArray(new String[0]);
    }

    /**
     * Gets the console gradient colors array.
     * 
     * @return array of gradient colors for console, or null if auto-generation is
     *         enabled
     */
    public String[] getConsoleGradient() {
        return console.isAutoGenerateColors() ? null : console.getGradient().toArray(new String[0]);
    }

    /**
     * Gets the chat colors for a specific message type.
     * 
     * @param type the message type
     * @return array of colors for the specified type, or null if auto-generation is
     *         enabled or type not found
     */
    public String[] getChatColors(String type) {
        if (chat.isAutoGenerateColors())
            return null;
        List<String> colors = chat.getColors().toMap().get(type);
        return colors != null ? colors.toArray(new String[0]) : null;
    }

    /**
     * Gets the console colors for a specific message type.
     * 
     * @param type the message type
     * @return array of colors for the specified type, or null if auto-generation is
     *         enabled or type not found
     */
    public String[] getConsoleColors(String type) {
        if (console.isAutoGenerateColors())
            return null;
        List<String> colors = console.getColors().toMap().get(type);
        return colors != null ? colors.toArray(new String[0]) : null;
    }

    /**
     * Checks if a sub-logger is enabled.
     * 
     * @param name the sub-logger name
     * @return true if the sub-logger is enabled or not configured (defaults to
     *         enabled)
     */
    public boolean isSubLoggerEnabled(String name) {
        SubLoggerConfig config = subLoggers.get(name);
        return config == null || config.isEnabled();
    }

    /**
     * Adds a new sub-logger to the configuration if it doesn't already exist.
     *
     * @param name the name of the sub-logger to add
     * @return true if the sub-logger was added, false otherwise
     */
    public boolean addSubLogger(String name) {
        if (subLoggers.containsKey(name)) {
            return false;
        }

        subLoggers.put(name, SubLoggerConfig.builder().enabled(true).build());
        return true;
    }

    /**
     * Prefix configuration for chat and console messages.
     *
     * @return defensive copy of the current prefix settings
     */
    public PrefixSettings getPrefix() {
        return copyPrefix(prefix);
    }

    /**
     * Replaces prefix settings.
     *
     * @param prefix prefix configuration to clone; null uses defaults
     */
    public void setPrefix(PrefixSettings prefix) {
        this.prefix = copyPrefix(prefix);
    }

    /**
     * Default logger options.
     *
     * @return defensive copy of default settings
     */
    public DefaultSettings getDefaults() {
        return copyDefaults(defaults);
    }

    /**
     * Sets default logger options.
     *
     * @param defaults default settings to copy; null uses defaults
     */
    public void setDefaults(DefaultSettings defaults) {
        this.defaults = copyDefaults(defaults);
    }

    /**
     * Chat-specific configuration.
     *
     * @return copy of chat settings
     */
    public ChatSettings getChat() {
        return copyChat(chat);
    }

    /**
     * Sets chat-specific configuration.
     *
     * @param chat chat settings to copy; null uses defaults
     */
    public void setChat(ChatSettings chat) {
        this.chat = copyChat(chat);
    }

    /**
     * Console-specific configuration.
     *
     * @return copy of console settings
     */
    public ConsoleSettings getConsole() {
        return copyConsole(console);
    }

    /**
     * Sets console-specific configuration.
     *
     * @param console console settings to copy; null uses defaults
     */
    public void setConsole(ConsoleSettings console) {
        this.console = copyConsole(console);
    }

    /**
     * Sub-logger configurations keyed by name.
     *
     * @return copy of registered sub-loggers
     */
    public Map<String, SubLoggerConfig> getSubLoggers() {
        return new LinkedHashMap<>(subLoggers);
    }

    /**
     * Replaces sub-logger configurations.
     *
     * @param subLoggers map of sub-loggers to copy; null clears the map
     */
    public void setSubLoggers(Map<String, SubLoggerConfig> subLoggers) {
        this.subLoggers = subLoggers != null ? new LinkedHashMap<>(subLoggers) : new LinkedHashMap<>();
    }

    /**
     * Gets the chat prefix mode.
     *
     * @return the prefix mode for chat messages
     */
    public PrefixMode getChatPrefixMode() {
        String mode = prefix != null ? prefix.getChatMode() : null;
        if (mode == null) {
            return PrefixMode.FULL;
        }
        try {
            return PrefixMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return PrefixMode.FULL;
        }
    }

    /**
     * Gets the console prefix mode.
     * 
     * @return the prefix mode for console messages
     */
    public PrefixMode getConsolePrefixMode() {
        String mode = prefix != null ? prefix.getConsoleMode() : null;
        if (mode == null) {
            return PrefixMode.SHORT;
        }
        try {
            return PrefixMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return PrefixMode.SHORT;
        }
    }

    /**
     * Gets the default target.
     * 
     * @return the default target for messages
     */
    public LogTarget getDefaultTarget() {
        String targetValue = defaults != null ? defaults.getTarget() : null;
        if (targetValue == null) {
            return LogTarget.BOTH;
        }
        try {
            return LogTarget.valueOf(targetValue);
        } catch (IllegalArgumentException e) {
            return LogTarget.BOTH;
        }
    }

    private PrefixSettings copyPrefix(PrefixSettings source) {
        PrefixSettings target = source != null ? new PrefixSettings() : null;
        if (target != null) {
            target.setChatMode(source.getChatMode());
            target.setConsoleMode(source.getConsoleMode());
            target.setCustom(source.getCustom());
            target.setUseGradientChat(source.isUseGradientChat());
            target.setUseGradientConsole(source.isUseGradientConsole());
            return target;
        }
        return new PrefixSettings();
    }

    private DefaultSettings copyDefaults(DefaultSettings source) {
        DefaultSettings target = new DefaultSettings();
        if (source != null) {
            target.setTarget(source.getTarget());
            target.setTextMaxLength(source.getTextMaxLength());
            target.setPlaceholderEngineOrder(source.getPlaceholderEngineOrder());
            target.setMiniPlaceholdersMode(source.getMiniPlaceholdersMode());
            target.setPb4Mode(source.getPb4Mode());
        }
        return target;
    }

    private ChatSettings copyChat(ChatSettings source) {
        ChatSettings target = new ChatSettings();
        if (source != null) {
            target.setAutoGenerateColors(source.isAutoGenerateColors());
            target.setGradient(source.getGradient());
            target.setColors(source.getColors());
        }
        return target;
    }

    private ConsoleSettings copyConsole(ConsoleSettings source) {
        ConsoleSettings target = new ConsoleSettings();
        if (source != null) {
            target.setAutoGenerateColors(source.isAutoGenerateColors());
            target.setGradient(source.getGradient());
            target.setStripFormatting(source.isStripFormatting());
            target.setColors(source.getColors());
        }
        return target;
    }

    /**
     * Resolves colors for given log level, using either chat or console settings.
     *
     * @param level log level
     * @param console true for console palette, false for chat
     * @return array of hex colors
     */
    public String[] resolveColors(LogLevel level, boolean console) {
        return console ? resolveConsoleColors(level) : resolveChatColors(level);
    }

    private String[] resolveChatColors(LogLevel level) {
        ChatSettings chatSettings = chat;
        if (chatSettings == null) {
            return fallback(level);
        }
        if (chatSettings.isAutoGenerateColors()) {
            return generateChatAuto(level);
        }
        String key = levelKey(level);
        if (key == null) {
            String[] gradient = getChatGradient();
            return gradient != null ? gradient : fallback(level);
        }
        String[] colors = getChatColors(key);
        if (colors != null) {
            return colors;
        }
        String[] gradient = getChatGradient();
        return gradient != null ? gradient : fallback(level);
    }

    private String[] resolveConsoleColors(LogLevel level) {
        ConsoleSettings consoleSettings = console;
        if (consoleSettings == null) {
            return fallback(level);
        }
        if (consoleSettings.isAutoGenerateColors()) {
            return generateConsoleAuto(level);
        }
        String key = levelKey(level);
        if (key == null) {
            String[] gradient = getConsoleGradient();
            return gradient != null ? gradient : fallback(level);
        }
        String[] colors = getConsoleColors(key);
        if (colors != null) {
            return colors;
        }
        String[] gradient = getConsoleGradient();
        return gradient != null ? gradient : fallback(level);
    }

    private String[] generateChatAuto(LogLevel level) {
        String[] base = ColorUtils.getMainAndSecondaryColor(safePluginName());
        return switch (level) {
            case ERROR -> new String[] { "#ff4444", ColorUtils.adjustHue(base[0], -30) };
            case WARN -> new String[] { "#ffaa00", ColorUtils.adjustHue(base[0], 45) };
            case DEBUG -> new String[] { "#00aaff", ColorUtils.adjustHue(base[0], 180) };
            case SUCCESS -> new String[] { "#00ff44", ColorUtils.adjustHue(base[0], 120) };
            default -> base;
        };
    }

    private String[] generateConsoleAuto(LogLevel level) {
        String[] base = ColorUtils.getMainAndSecondaryColor(safePluginName());
        String[] gradient = new String[] {
                ColorUtils.adjustBrightness(base[0], 1.2f),
                ColorUtils.adjustBrightness(base[1], 1.2f)
        };
        return switch (level) {
            case ERROR -> new String[] { "#ff6666", ColorUtils.adjustHue(base[0], -20) };
            case WARN -> new String[] { "#ffcc22", ColorUtils.adjustHue(base[0], 55) };
            case DEBUG -> new String[] { "#22aaff", ColorUtils.adjustHue(base[0], 170) };
            case SUCCESS -> new String[] { "#22ff66", ColorUtils.adjustHue(base[0], 110) };
            default -> gradient;
        };
    }

    private String[] fallback(LogLevel level) {
        return defaultColors(level);
    }

    /**
     * Provides fallback colors when no configuration is available.
     *
     * @param level log level to resolve
     * @return gradient colors for the specified level
     */
    public static String[] defaultColors(LogLevel level) {
        return switch (level) {
            case ERROR -> DEFAULT_ALERT.clone();
            case WARN -> DEFAULT_WARN.clone();
            case DEBUG -> DEFAULT_DEBUG.clone();
            case SUCCESS -> DEFAULT_SUCCESS.clone();
            default -> DEFAULT_GRADIENT.clone();
        };
    }

    private String levelKey(LogLevel level) {
        return switch (level) {
            case ERROR -> "error";
            case WARN -> "warn";
            case DEBUG -> "debug";
            case SUCCESS -> "success";
            default -> null;
        };
    }

    private String safePluginName() {
        return (pluginName == null || pluginName.isEmpty()) ? "MagicUtils" : pluginName;
    }
}
