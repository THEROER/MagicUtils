package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.lang.LanguageManager;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for logging messages to console and chat with color and formatting support.
 */
public final class Logger {
    private static LoggerConfig config;
    private static ConfigManager configManager;
    
    private static String[] chatDefaultGradient;
    private static String[] consoleDefaultGradient;
    
    private static String[] chatErrorColors;
    private static String[] chatWarnColors;
    private static String[] chatDebugColors;
    private static String[] chatSuccessColors;
    
    private static String[] consoleErrorColors;
    private static String[] consoleWarnColors;
    private static String[] consoleDebugColors;
    private static String[] consoleSuccessColors;

    
    @Setter
    private static LanguageManager languageManager;
    
    /**
     * Sets auto-localization state.
     * @param enabled true to enable auto-localization
     */
    public static void setAutoLocalization(boolean enabled) {
        if (config != null) {
            config.setAutoLocalization(enabled);
            configManager.save(LoggerConfig.class);
        }
    }
    private static MiniMessage mm = MiniMessage.miniMessage();
    
    private static final Map<String, SubLogger> subLoggers = new HashMap<>();

    /**
     * Default constructor (not used).
     */
    private Logger() {}

    /**
     * Initializes the logger with the plugin and configuration manager.
     * @param plugin the JavaPlugin instance
     * @param manager the configuration manager
     */
    public static void init(JavaPlugin plugin, ConfigManager manager) {
        Logger.configManager = manager;
        Logger.config = manager.register(LoggerConfig.class);
        
        // Set plugin name if not already set
        if (config.getPluginName().isEmpty()) {
            config.setPluginName(plugin.getName());
        }
        
        // Generate short name if not set
        if (config.getShortName().isEmpty()) {
            config.setShortName(generateShortName(config.getPluginName()));
        }
        
        loadConfiguration();
        
        // Save config with updated values
        manager.save(LoggerConfig.class);
        
        // Listen for config changes
        manager.onChange(LoggerConfig.class, (cfg, sections) -> {
            config = cfg;
            loadConfiguration();
        });
    }

    /**
     * Reloads the logger configuration.
     */
    public static void reload() {
        if (config != null) {
            loadConfiguration();
        }
    }

    /**
     * Load all settings from configuration
     */
    private static void loadConfiguration() {
        if (config == null) return;
        
        if (config.getChat().isAutoGenerateColors()) {
            generateChatColors();
        } else {
            loadChatColorsFromConfig();
        }
        
        if (config.getConsole().isAutoGenerateColors()) {
            generateConsoleColors();
        } else {
            loadConsoleColorsFromConfig();
        }
        
        // Load sub-logger configurations
        loadSubLoggers();
    }

    /**
     * Generate chat colors automatically
     */
    private static void generateChatColors() {
        String[] baseColors = getMainAndSecondaryColor(config.getPluginName());
        chatDefaultGradient = baseColors;
        chatErrorColors = new String[]{"#ff4444", adjustHue(baseColors[0], -30)};
        chatWarnColors = new String[]{"#ffaa00", adjustHue(baseColors[0], 45)};
        chatDebugColors = new String[]{"#00aaff", adjustHue(baseColors[0], 180)};
        chatSuccessColors = new String[]{"#00ff44", adjustHue(baseColors[0], 120)};
    }

    /**
     * Generate console colors automatically
     */
    private static void generateConsoleColors() {
        String[] baseColors = getMainAndSecondaryColor(config.getPluginName());
        consoleDefaultGradient = new String[]{
            adjustBrightness(baseColors[0], 1.2f),
            adjustBrightness(baseColors[1], 1.2f)
        };
        consoleErrorColors = new String[]{"#ff6666", adjustHue(baseColors[0], -20)};
        consoleWarnColors = new String[]{"#ffcc22", adjustHue(baseColors[0], 55)};
        consoleDebugColors = new String[]{"#22aaff", adjustHue(baseColors[0], 170)};
        consoleSuccessColors = new String[]{"#22ff66", adjustHue(baseColors[0], 110)};
    }

    /**
     * Load chat colors from configuration
     */
    private static void loadChatColorsFromConfig() {
        var gradient = config.getChatGradient();
        if (gradient != null) {
            chatDefaultGradient = gradient;
        }
        
        var errorColors = config.getChatColors("error");
        if (errorColors != null) {
            chatErrorColors = errorColors;
        }
        
        var warnColors = config.getChatColors("warn");
        if (warnColors != null) {
            chatWarnColors = warnColors;
        }
        
        var debugColors = config.getChatColors("debug");
        if (debugColors != null) {
            chatDebugColors = debugColors;
        }
        
        var successColors = config.getChatColors("success");
        if (successColors != null) {
            chatSuccessColors = successColors;
        }
    }

    /**
     * Load console colors from configuration
     */
    private static void loadConsoleColorsFromConfig() {
        var gradient = config.getConsoleGradient();
        if (gradient != null) {
            consoleDefaultGradient = gradient;
        }
        
        var errorColors = config.getConsoleColors("error");
        if (errorColors != null) {
            consoleErrorColors = errorColors;
        }
        
        var warnColors = config.getConsoleColors("warn");
        if (warnColors != null) {
            consoleWarnColors = warnColors;
        }
        
        var debugColors = config.getConsoleColors("debug");
        if (debugColors != null) {
            consoleDebugColors = debugColors;
        }
        
        var successColors = config.getConsoleColors("success");
        if (successColors != null) {
            consoleSuccessColors = successColors;
        }
    }

    /**
     * Generate short name from plugin name
     */
    private static String generateShortName(String name) {
        StringBuilder sb = new StringBuilder();
        Pattern pattern = Pattern.compile("[A-Z]");
        Matcher matcher = pattern.matcher(name);
        
        while (matcher.find() && sb.length() < 3) {
            sb.append(matcher.group());
        }
        
        if (sb.length() < 2) {
            sb = new StringBuilder();
            String[] words = name.split("(?=[A-Z])|[\\s_-]+");
            for (String word : words) {
                if (!word.isEmpty() && sb.length() < 3) {
                    sb.append(word.charAt(0));
                }
            }
        }
        
        return sb.toString().toUpperCase();
    }

    /**
     * Adjust color brightness by factor
     */
    private static String adjustBrightness(String hex, float factor) {
        int r = Integer.valueOf(hex.substring(1, 3), 16);
        int g = Integer.valueOf(hex.substring(3, 5), 16);
        int b = Integer.valueOf(hex.substring(5, 7), 16);
        
        r = Math.min(255, Math.round(r * factor));
        g = Math.min(255, Math.round(g * factor));
        b = Math.min(255, Math.round(b * factor));
        
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Adjust color hue by degrees
     */
    private static String adjustHue(String hex, int hueShift) {
        int r = Integer.valueOf(hex.substring(1, 3), 16);
        int g = Integer.valueOf(hex.substring(3, 5), 16);
        int b = Integer.valueOf(hex.substring(5, 7), 16);
        
        int shift = Math.abs(hueShift) % 360;
        if (shift > 120) {
            int temp = r; r = g; g = b; b = temp;
        } else if (shift > 60) {
            int temp = r; r = b; b = g; g = temp;
        }
        
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Gets the main and secondary color for a given name.
     * @param name the name to generate colors for
     * @return an array with main and secondary color
     */
    public static String[] getMainAndSecondaryColor(String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(name.getBytes());

            int r = 100 + (hash[0] & 0xFF) % 156;
            int g = 100 + (hash[1] & 0xFF) % 156;
            int b = 100 + (hash[2] & 0xFF) % 156;

            String primary = String.format("#%02x%02x%02x", r, g, b);

            int r2 = Math.min(255, r + 40);
            int g2 = Math.min(255, g + 50);
            int b2 = Math.max(50, b - 20);

            String secondary = String.format("#%02x%02x%02x", r2, g2, b2);

            return new String[]{primary, secondary};
        } catch (NoSuchAlgorithmException e) {
            return new String[]{"#7c3aed", "#ec4899"};
        }
    }

    /**
     * Logs a message to the console and chat.
     * @param message the message to log
     */
    public static void log(String message) {
        log(null, message, false);
    }
    
    /**
     * Logs a message to the console and chat.
     * @param message the message to log
     */
    public static void log(Component message) {
        log(null, message, false);
    }
    
    /**
     * Logs a message to the console and chat for a specific player.
     * @param player the player
     * @param message the message to log
     */
    public static void log(Player player, String message) {
        log(player, message, false);
    }
    
    /**
     * Logs a message to the console and chat for a specific player.
     * @param player the player
     * @param message the message to log
     */
    public static void log(Player player, Component message) {
        log(player, message, false);
    }
    
    /**
     * Logs a message to the console and chat with an option to send to all.
     * @param message the message to log
     * @param all whether to send to all
     */
    public static void log(String message, boolean all) {
        log(null, message, all);
    }
    
    /**
     * Logs a message to the console and chat with an option to send to all.
     * @param message the message to log
     * @param all whether to send to all
     */
    public static void log(Component message, boolean all) {
        log(null, message, all);
    }
    
    /**
     * Logs a message to the console and chat for a specific player with an option to send to all.
     * @param player the player
     * @param message the message to log
     * @param all whether to send to all
     */
    public static void log(Player player, String message, boolean all) {
        sendMessage(LogLevel.INFO, player, message, all);
    }
    
    /**
     * Logs a message to the console and chat for a specific player with an option to send to all.
     * @param player the player
     * @param message the message to log
     * @param all whether to send to all
     */
    public static void log(Player player, Component message, boolean all) {
        sendMessage(LogLevel.INFO, player, message, all);
    }

    /**
     * Sends an info message to the console and chat.
     * @param message the info message
     */
    public static void info(String message) {
        info(null, message, false);
    }
    
    /**
     * Sends an info message to the console and chat.
     * @param message the info message
     */
    public static void info(Component message) {
        info(null, message, false);
    }
    
    /**
     * Sends an info message to a player.
     * @param player the player
     * @param message the info message
     */
    public static void info(Player player, String message) {
        info(player, message, false);
    }
    
    /**
     * Sends an info message to a player.
     * @param player the player
     * @param message the info message
     */
    public static void info(Player player, Component message) {
        info(player, message, false);
    }
    
    /**
     * Sends an info message to the console and chat with an option to send to all.
     * @param message the info message
     * @param all whether to send to all
     */
    public static void info(String message, boolean all) {
        info(null, message, all);
    }
    
    /**
     * Sends an info message to the console and chat with an option to send to all.
     * @param message the info message
     * @param all whether to send to all
     */
    public static void info(Component message, boolean all) {
        info(null, message, all);
    }
    
    /**
     * Sends an info message to a player with an option to send to all.
     * @param player the player
     * @param message the info message
     * @param all whether to send to all
     */
    public static void info(Player player, String message, boolean all) {
        sendMessage(LogLevel.INFO, player, message, all);
    }
    
    /**
     * Sends an info message to a player with an option to send to all.
     * @param player the player
     * @param message the info message
     * @param all whether to send to all
     */
    public static void info(Player player, Component message, boolean all) {
        sendMessage(LogLevel.INFO, player, message, all);
    }

    /**
     * Sends a warning message to the console and chat.
     * @param message the warning message
     */
    public static void warn(String message) {
        warn(null, message, false);
    }
    
    /**
     * Sends a warning message to the console and chat.
     * @param message the warning message
     */
    public static void warn(Component message) {
        warn(null, message, false);
    }
    
    /**
     * Sends a warning message to a player.
     * @param player the player
     * @param message the warning message
     */
    public static void warn(Player player, String message) {
        warn(player, message, false);
    }
    
    /**
     * Sends a warning message to a player.
     * @param player the player
     * @param message the warning message
     */
    public static void warn(Player player, Component message) {
        warn(player, message, false);
    }
    
    /**
     * Sends a warning message to the console and chat with an option to send to all.
     * @param message the warning message
     * @param all whether to send to all
     */
    public static void warn(String message, boolean all) {
        warn(null, message, all);
    }
    
    /**
     * Sends a warning message to the console and chat with an option to send to all.
     * @param message the warning message
     * @param all whether to send to all
     */
    public static void warn(Component message, boolean all) {
        warn(null, message, all);
    }
    
    /**
     * Sends a warning message to a player with an option to send to all.
     * @param player the player
     * @param message the warning message
     * @param all whether to send to all
     */
    public static void warn(Player player, String message, boolean all) {
        sendMessage(LogLevel.WARN, player, message, all);
    }
    
    /**
     * Sends a warning message to a player with an option to send to all.
     * @param player the player
     * @param message the warning message
     * @param all whether to send to all
     */
    public static void warn(Player player, Component message, boolean all) {
        sendMessage(LogLevel.WARN, player, message, all);
    }

    /**
     * Sends an error message to the console and chat.
     * @param message the error message
     */
    public static void error(String message) {
        error(null, message, false);
    }
    
    /**
     * Sends an error message to the console and chat.
     * @param message the error message
     */
    public static void error(Component message) {
        error(null, message, false);
    }
    
    /**
     * Sends an error message to a player.
     * @param player the player
     * @param message the error message
     */
    public static void error(Player player, String message) {
        error(player, message, false);
    }
    
    /**
     * Sends an error message to a player.
     * @param player the player
     * @param message the error message
     */
    public static void error(Player player, Component message) {
        error(player, message, false);
    }
    
    /**
     * Sends an error message to the console and chat with an option to send to all.
     * @param message the error message
     * @param all whether to send to all
     */
    public static void error(String message, boolean all) {
        error(null, message, all);
    }
    
    /**
     * Sends an error message to the console and chat with an option to send to all.
     * @param message the error message
     * @param all whether to send to all
     */
    public static void error(Component message, boolean all) {
        error(null, message, all);
    }
    
    /**
     * Sends an error message to a player with an option to send to all.
     * @param player the player
     * @param message the error message
     * @param all whether to send to all
     */
    public static void error(Player player, String message, boolean all) {
        sendMessage(LogLevel.ERROR, player, message, all);
    }
    
    /**
     * Sends an error message to a player with an option to send to all.
     * @param player the player
     * @param message the error message
     * @param all whether to send to all
     */
    public static void error(Player player, Component message, boolean all) {
        sendMessage(LogLevel.ERROR, player, message, all);
    }

    /**
     * Sends a debug message to the console and chat.
     * @param message the debug message
     */
    public static void debug(String message) {
        debug(null, message, false);
    }
    
    /**
     * Sends a debug message to the console and chat.
     * @param message the debug message
     */
    public static void debug(Component message) {
        debug(null, message, false);
    }
    
    /**
     * Sends a debug message to a player.
     * @param player the player
     * @param message the debug message
     */
    public static void debug(Player player, String message) {
        debug(player, message, false);
    }
    
    /**
     * Sends a debug message to a player.
     * @param player the player
     * @param message the debug message
     */
    public static void debug(Player player, Component message) {
        debug(player, message, false);
    }
    
    /**
     * Sends a debug message to the console and chat with an option to send to all.
     * @param message the debug message
     * @param all whether to send to all
     */
    public static void debug(String message, boolean all) {
        debug(null, message, all);
    }
    
    /**
     * Sends a debug message to the console and chat with an option to send to all.
     * @param message the debug message
     * @param all whether to send to all
     */
    public static void debug(Component message, boolean all) {
        debug(null, message, all);
    }
    
    /**
     * Sends a debug message to a player with an option to send to all.
     * @param player the player
     * @param message the debug message
     * @param all whether to send to all
     */
    public static void debug(Player player, String message, boolean all) {
        sendMessage(LogLevel.DEBUG, player, message, all);
    }
    
    /**
     * Sends a debug message to a player with an option to send to all.
     * @param player the player
     * @param message the debug message
     * @param all whether to send to all
     */
    public static void debug(Player player, Component message, boolean all) {
        sendMessage(LogLevel.DEBUG, player, message, all);
    }

    /**
     * Sends a success message to the console and chat.
     * @param message the success message
     */
    public static void success(String message) {
        success(null, message, false);
    }
    
    /**
     * Sends a success message to the console and chat.
     * @param message the success message
     */
    public static void success(Component message) {
        success(null, message, false);
    }
    
    /**
     * Sends a success message to a player.
     * @param player the player
     * @param message the success message
     */
    public static void success(Player player, String message) {
        success(player, message, false);
    }
    
    /**
     * Sends a success message to a player.
     * @param player the player
     * @param message the success message
     */
    public static void success(Player player, Component message) {
        success(player, message, false);
    }
    
    /**
     * Sends a success message to the console and chat with an option to send to all.
     * @param message the success message
     * @param all whether to send to all
     */
    public static void success(String message, boolean all) {
        success(null, message, all);
    }
    
    /**
     * Sends a success message to the console and chat with an option to send to all.
     * @param message the success message
     * @param all whether to send to all
     */
    public static void success(Component message, boolean all) {
        success(null, message, all);
    }
    
    /**
     * Sends a success message to a player with an option to send to all.
     * @param player the player
     * @param message the success message
     * @param all whether to send to all
     */
    public static void success(Player player, String message, boolean all) {
        sendMessage(LogLevel.SUCCESS, player, message, all);
    }
    
    /**
     * Sends a success message to a player with an option to send to all.
     * @param player the player
     * @param message the success message
     * @param all whether to send to all
     */
    public static void success(Player player, Component message, boolean all) {
        sendMessage(LogLevel.SUCCESS, player, message, all);
    }

    /**
     * Internal method to send messages with all logic
     */
    private static void sendMessage(LogLevel level, Player player, Object message, boolean all) {
        // Skip debug messages related to commands if debugCommands is disabled
        if (level == LogLevel.DEBUG && !config.isDebugCommands()) {
            String messageStr = message.toString().toLowerCase();
            if (messageStr.contains("command") || messageStr.contains("executing") || 
                messageStr.contains("parsing") || messageStr.contains("method") ||
                messageStr.contains("argument") || messageStr.contains("subcommand")) {
                return;
            }
        }
        
        String finalMessage = (message instanceof Component) ? message.toString() : (String) message;
        
        if (config.isAutoLocalization() && languageManager != null && !(message instanceof Component)) {
            String localizedMessage = languageManager.getMessage(finalMessage);
            if (!localizedMessage.equals(finalMessage)) {
                finalMessage = localizedMessage;
            }
        }
        
        Component component = (message instanceof Component) ? (Component) message : 
            createFormattedMessage(level, finalMessage, player == null);

        if (all) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(component);
            }
            Bukkit.getConsoleSender().sendMessage(component);
        } else if (player != null) {
            player.sendMessage(component);
        } else {
            Bukkit.getConsoleSender().sendMessage(component);
        }
    }

    /**
     * Create formatted message component
     */
    private static Component createFormattedMessage(LogLevel level, String message, boolean forConsole) {
        String[] colors = getColorsForLevel(level, forConsole);
        String gradientTag = createGradientTag(colors);

        String prefix = (level == LogLevel.INFO) ? config.getPluginName() : config.getShortName() + " " + level.name();
        String fullMessage = gradientTag + "[" + prefix + "] " + message + "</gradient>";
        
        return mm.deserialize(fullMessage, TagResolver.standard());
    }

    /**
     * Get colors array for specific log level
     */
    private static String[] getColorsForLevel(LogLevel level, boolean forConsole) {
        if (forConsole) {
            return switch (level) {
                case ERROR -> consoleErrorColors;
                case WARN -> consoleWarnColors;
                case DEBUG -> consoleDebugColors;
                case SUCCESS -> consoleSuccessColors;
                default -> consoleDefaultGradient;
            };
        } else {
            return switch (level) {
                case ERROR -> chatErrorColors;
                case WARN -> chatWarnColors;
                case DEBUG -> chatDebugColors;
                case SUCCESS -> chatSuccessColors;
                default -> chatDefaultGradient;
            };
        }
    }

    /**
     * Create gradient tag from colors array
     */
    private static String createGradientTag(String[] colors) {
        StringBuilder sb = new StringBuilder("<gradient");
        for (String color : colors) {
            sb.append(":").append(color);
        }
        sb.append(">");
        return sb.toString();
    }

    /**
     * Load sub-logger configurations from config
     */
    private static void loadSubLoggers() {
        if (config == null || config.getSubLoggers() == null) return;
        
        for (Map.Entry<String, SubLoggerConfig> entry : config.getSubLoggers().entrySet()) {
            SubLogger existing = subLoggers.get(entry.getKey());
            if (existing != null) {
                existing.setEnabled(entry.getValue().isEnabled());
            }
        }
    }
    
    /**
     * Creates or gets a sub-logger with the given name
     * @param name the name of the sub-logger
     * @return the sub-logger instance
     */
    public static SubLogger getSubLogger(String name) {
        return getSubLogger(name, "[" + name + "]");
    }
    
    /**
     * Creates or gets a sub-logger with the given name and prefix
     * @param name the name of the sub-logger
     * @param prefix the prefix to use
     * @return the sub-logger instance
     */
    public static SubLogger getSubLogger(String name, String prefix) {
        return subLoggers.computeIfAbsent(name, k -> {
            SubLogger subLogger = new SubLogger(name, prefix);
            
            // Check if there's a configuration for this sub-logger
            if (config != null && config.getSubLoggers() != null) {
                SubLoggerConfig subLoggerConfig = config.getSubLoggers().get(name);
                if (subLoggerConfig != null) {
                    subLogger.setEnabled(subLoggerConfig.isEnabled());
                }
            }
            
            return subLogger;
        });
    }
    
    /**
     * Gets all registered sub-loggers
     * @return map of sub-logger names to instances
     */
    public static Map<String, SubLogger> getSubLoggers() {
        return new HashMap<>(subLoggers);
    }
    
    /**
     * Sets the enabled state of a sub-logger
     * @param name the name of the sub-logger
     * @param enabled true to enable, false to disable
     */
    public static void setSubLoggerEnabled(String name, boolean enabled) {
        SubLogger subLogger = subLoggers.get(name);
        if (subLogger != null) {
            subLogger.setEnabled(enabled);
        }
    }
    
    /**
     * Logging levels enumeration.
     */
    public enum LogLevel {
        /** Info log level. */
        INFO("INFO"),
        /** Warn log level. */
        WARN("WARN"),
        /** Error log level. */
        ERROR("ERROR"),
        /** Debug log level. */
        DEBUG("DEBUG"),
        /** Success log level. */
        SUCCESS("SUCCESS");
        
        private final String displayName;
        
        LogLevel(String displayName) {
            this.displayName = displayName;
        }
        
        /**
         * Gets the display name of the log level.
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }
}