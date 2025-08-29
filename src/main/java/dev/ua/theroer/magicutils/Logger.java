package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.lang.LanguageManager;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.logger.LogBuilder;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for logging messages to console and chat with color and
 * formatting support.
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
     * 
     * @param enabled true to enable auto-localization
     */
    public static void setAutoLocalization(boolean enabled) {
        if (config != null) {
            config.setAutoLocalization(enabled);
            configManager.save(LoggerConfig.class);
        }
    }

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Map<String, PrefixedLogger> prefixedLoggers = new HashMap<>();

    /**
     * Default constructor (not used).
     */
    private Logger() {
    }

    /**
     * Initializes the logger with the plugin and configuration manager.
     * 
     * @param plugin  the JavaPlugin instance
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
        if (config == null)
            return;

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
        chatErrorColors = new String[] { "#ff4444", adjustHue(baseColors[0], -30) };
        chatWarnColors = new String[] { "#ffaa00", adjustHue(baseColors[0], 45) };
        chatDebugColors = new String[] { "#00aaff", adjustHue(baseColors[0], 180) };
        chatSuccessColors = new String[] { "#00ff44", adjustHue(baseColors[0], 120) };
    }

    /**
     * Generate console colors automatically
     */
    private static void generateConsoleColors() {
        String[] baseColors = getMainAndSecondaryColor(config.getPluginName());
        consoleDefaultGradient = new String[] {
                adjustBrightness(baseColors[0], 1.2f),
                adjustBrightness(baseColors[1], 1.2f)
        };
        consoleErrorColors = new String[] { "#ff6666", adjustHue(baseColors[0], -20) };
        consoleWarnColors = new String[] { "#ffcc22", adjustHue(baseColors[0], 55) };
        consoleDebugColors = new String[] { "#22aaff", adjustHue(baseColors[0], 170) };
        consoleSuccessColors = new String[] { "#22ff66", adjustHue(baseColors[0], 110) };
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
            int temp = r;
            r = g;
            g = b;
            b = temp;
        } else if (shift > 60) {
            int temp = r;
            r = b;
            b = g;
            g = temp;
        }

        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Gets the main and secondary color for a given name.
     * 
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

            return new String[] { primary, secondary };
        } catch (NoSuchAlgorithmException e) {
            return new String[] { "#7c3aed", "#ec4899" };
        }
    }

    /**
     * Universal send method for logging messages to console.
     * 
     * @param level   the log level (INFO, WARN, ERROR, DEBUG, SUCCESS)
     * @param message the message to log (String or Component)
     */
    public static void send(LogLevel level, Object message) {
        send(level, message, null, false);
    }

    /**
     * Universal send method for logging messages to a specific player.
     * 
     * @param level   the log level (INFO, WARN, ERROR, DEBUG, SUCCESS)
     * @param message the message to log (String or Component)
     * @param player  the target player to send the message to
     */
    public static void send(LogLevel level, Object message, Player player) {
        send(level, message, player, false);
    }

    /**
     * Universal send method with all parameters for complete control over message
     * delivery.
     * 
     * @param level   the log level (INFO, WARN, ERROR, DEBUG, SUCCESS)
     * @param message the message to log (String or Component)
     * @param player  the target player (can be null for console-only)
     * @param all     if true, sends to all online players; if false, sends to
     *                specified player or console
     */
    public static void send(LogLevel level, Object message, Player player, boolean all) {
        sendMessage(level, player, message, all);
    }

    // Simple methods for quick usage - kept for backward compatibility
    /**
     * Logs an info message to console.
     * 
     * @param message the message to log
     */
    public static void log(String message) {
        send(LogLevel.INFO, message);
    }

    /**
     * Logs an info component message to console.
     * 
     * @param message the component message to log
     */
    public static void log(Component message) {
        send(LogLevel.INFO, message);
    }

    // Fluent API for advanced usage
    /**
     * Creates a fluent log builder for info level messages.
     * 
     * @return a new LogBuilder instance for chaining
     */
    public static LogBuilder log() {
        return new LogBuilder(LogLevel.INFO);
    }

    /**
     * Creates a fluent log builder for info level messages.
     * 
     * @return a new LogBuilder instance for chaining
     */
    public static LogBuilder info() {
        return new LogBuilder(LogLevel.INFO);
    }

    /**
     * Creates a fluent log builder for warning level messages.
     * 
     * @return a new LogBuilder instance for chaining
     */
    public static LogBuilder warn() {
        return new LogBuilder(LogLevel.WARN);
    }

    /**
     * Creates a fluent log builder for error level messages.
     * 
     * @return a new LogBuilder instance for chaining
     */
    public static LogBuilder error() {
        return new LogBuilder(LogLevel.ERROR);
    }

    /**
     * Creates a fluent log builder for debug level messages.
     * 
     * @return a new LogBuilder instance for chaining
     */
    public static LogBuilder debug() {
        return new LogBuilder(LogLevel.DEBUG);
    }

    /**
     * Creates a fluent log builder for success level messages.
     * 
     * @return a new LogBuilder instance for chaining
     */
    public static LogBuilder success() {
        return new LogBuilder(LogLevel.SUCCESS);
    }

    /**
     * Creates a new logger instance with custom prefix.
     * Usage: Logger.create("MyModule").info("Hello")
     * 
     * @param name the name of the prefixed logger
     * @return a new PrefixedLogger instance
     */
    public static PrefixedLogger create(String name) {
        return withPrefix(name);
    }

    /**
     * Creates a new logger instance with custom name and prefix.
     * Usage: Logger.create("MyModule", "[MODULE]").info("Hello")
     * 
     * @param name   the name of the prefixed logger
     * @param prefix the custom prefix to use in messages
     * @return a new PrefixedLogger instance
     */
    public static PrefixedLogger create(String name, String prefix) {
        return withPrefix(name, prefix);
    }

    // Info methods - simplified
    /**
     * Logs an info level message to console.
     * 
     * @param message the info message to log
     */
    public static void info(String message) {
        send(LogLevel.INFO, message);
    }

    /**
     * Logs an info level component message to console.
     * 
     * @param message the info component message to log
     */
    public static void info(Component message) {
        send(LogLevel.INFO, message);
    }

    // Warn methods - simplified
    /**
     * Logs a warning level message to console.
     * 
     * @param message the warning message to log
     */
    public static void warn(String message) {
        send(LogLevel.WARN, message);
    }

    /**
     * Logs a warning level component message to console.
     * 
     * @param message the warning component message to log
     */
    public static void warn(Component message) {
        send(LogLevel.WARN, message);
    }

    // Error methods - simplified
    /**
     * Logs an error level message to console.
     * 
     * @param message the error message to log
     */
    public static void error(String message) {
        send(LogLevel.ERROR, message);
    }

    /**
     * Logs an error level component message to console.
     * 
     * @param message the error component message to log
     */
    public static void error(Component message) {
        send(LogLevel.ERROR, message);
    }

    // Debug methods - simplified
    /**
     * Logs a debug level message to console.
     * 
     * @param message the debug message to log
     */
    public static void debug(String message) {
        send(LogLevel.DEBUG, message);
    }

    /**
     * Logs a debug level component message to console.
     * 
     * @param message the debug component message to log
     */
    public static void debug(Component message) {
        send(LogLevel.DEBUG, message);
    }

    // Success methods - simplified
    /**
     * Logs a success level message to console.
     * 
     * @param message the success message to log
     */
    public static void success(String message) {
        send(LogLevel.SUCCESS, message);
    }

    /**
     * Logs a success level component message to console.
     * 
     * @param message the success component message to log
     */
    public static void success(Component message) {
        send(LogLevel.SUCCESS, message);
    }

    /**
     * Internal method to send messages with all logic
     */
    private static void sendMessage(LogLevel level, Player player, Object message, boolean all) {
        String finalMessage = (message instanceof String) ? (String) message : null;

        if (languageManager != null && config.isAutoLocalization() && languageManager != null) {
            String localizedMessage = languageManager.getMessage(finalMessage);
            if (!localizedMessage.equals(finalMessage)) {
                finalMessage = localizedMessage;
            }
        }

        Component component = (message instanceof Component) ? (Component) message
                : createFormattedMessage(level, finalMessage == null ? String.valueOf(message) : finalMessage,
                        player == null);

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

    private static final Map<Character, String> LEGACY_TO_MM = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private static String legacyToMiniMessage(String in) {
        if (in == null || in.isEmpty())
            return in;

        String s = in.replace('§', '&');

        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length();) {
            if (i + 13 < s.length()
                    && s.charAt(i) == '&'
                    && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')
                    && s.charAt(i + 2) == '&' && s.charAt(i + 4) == '&'
                    && s.charAt(i + 6) == '&' && s.charAt(i + 8) == '&'
                    && s.charAt(i + 10) == '&' && s.charAt(i + 12) == '&') {

                char a = s.charAt(i + 3), b = s.charAt(i + 5), c = s.charAt(i + 7),
                        d = s.charAt(i + 9), e = s.charAt(i + 11), f = s.charAt(i + 13);
                String hex = ("" + a + b + c + d + e + f).toLowerCase();
                out.append("<#").append(hex).append(">");
                i += 14;
                continue;
            }
            if (i + 7 <= s.length() && s.charAt(i) == '&' && s.charAt(i + 1) == '#') {
                String hex = s.substring(i + 2, i + 8);
                if (hex.matches("(?i)[0-9a-f]{6}")) {
                    out.append("<#").append(hex).append(">");
                    i += 8;
                    continue;
                }
            }
            if (i + 1 < s.length() && s.charAt(i) == '&') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String tag = LEGACY_TO_MM.get(code);
                if (tag != null) {
                    out.append(tag);
                    i += 2;
                    continue;
                }
            }
            out.append(s.charAt(i++));
        }
        return out.toString();
    }

    /**
     * Create formatted message component
     */
    private static Component createFormattedMessage(LogLevel level, String message, boolean forConsole) {
        String[] colors = getColorsForLevel(level, forConsole);
        String gradientTag = createGradientTag(colors);

        String prefix = (level == LogLevel.INFO) ? config.getPluginName() : config.getShortName() + " " + level.name();
        String body = legacyToMiniMessage(message);
        String fullMessage = "<reset>" + gradientTag + "[" + prefix + "] " + body + "</gradient>";

        return mm.deserialize(fullMessage, TagResolver.standard());
    }

    public static Component parseSmart(String input) {
        if (input == null || input.isEmpty())
            return Component.empty();
        boolean hasMini = input.indexOf('<') >= 0 && input.indexOf('>') > input.indexOf('<');
        boolean hasLegacy = input.indexOf('&') >= 0 || input.indexOf('§') >= 0;
        if (hasMini && hasLegacy) {
            return mm.deserialize(legacyToMiniMessage(input));
        } else if (hasMini) {
            return mm.deserialize(input);
        } else if (hasLegacy) {
            return LEGACY.deserialize(input.replace('§', '&'));
        }
        return Component.text(input);
    }

    /** Быстрый broadcast на всех (INFO). */
    public static void broadcast(Object message) {
        send(LogLevel.INFO, message, null, true);
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
     * Load prefixed logger configurations from config
     */
    private static void loadSubLoggers() {
        if (config == null || config.getSubLoggers() == null)
            return;

        for (Map.Entry<String, SubLoggerConfig> entry : config.getSubLoggers().entrySet()) {
            PrefixedLogger existing = prefixedLoggers.get(entry.getKey());
            if (existing != null) {
                existing.setEnabled(entry.getValue().isEnabled());
            }
        }
    }

    /**
     * Creates or gets a logger with custom prefix
     * 
     * @param name the name of the logger
     * @return the prefixed logger instance
     */
    public static PrefixedLogger withPrefix(String name) {
        return withPrefix(name, "[" + name + "]");
    }

    /**
     * Creates or gets a logger with custom prefix
     * 
     * @param name   the name of the logger
     * @param prefix the prefix to use
     * @return the prefixed logger instance
     */
    public static PrefixedLogger withPrefix(String name, String prefix) {
        return prefixedLoggers.computeIfAbsent(name, k -> {
            PrefixedLogger prefixedLogger = new PrefixedLogger(name, prefix);

            // Check if there's a configuration for this logger
            if (config != null && config.getSubLoggers() != null) {
                SubLoggerConfig subLoggerConfig = config.getSubLoggers().get(name);
                if (subLoggerConfig != null) {
                    prefixedLogger.setEnabled(subLoggerConfig.isEnabled());
                }
            }

            return prefixedLogger;
        });
    }

    /**
     * Gets all registered prefixed loggers
     * 
     * @return map of logger names to instances
     */
    public static Map<String, PrefixedLogger> getPrefixedLoggers() {
        return new HashMap<>(prefixedLoggers);
    }

    /**
     * Sets the enabled state of a prefixed logger
     * 
     * @param name    the name of the logger
     * @param enabled true to enable, false to disable
     */
    public static void setPrefixedLoggerEnabled(String name, boolean enabled) {
        PrefixedLogger logger = prefixedLoggers.get(name);
        if (logger != null) {
            logger.setEnabled(enabled);
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
         * 
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }
}