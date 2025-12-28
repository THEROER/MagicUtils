package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigNamespaceProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.jetbrains.annotations.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform-agnostic logger core with configurable formatting and placeholders.
 */
@LogMethods(staticMethods = false, audienceType = "dev.ua.theroer.magicutils.platform.Audience")
@SuppressWarnings("doclint:missing")
public class LoggerCore extends LoggerCoreMethods {
    private static final String LOGGER_DIR_PLACEHOLDER = "logger_dir";
    private static final String LOGGER_DIR_DEFAULT = ".";
    private static final String LOGGER_BASE_NAME = "logger";
    private static final List<String> LOGGER_EXTENSIONS = List.of("yml", "yaml", "jsonc", "json", "toml");

    @Getter
    private LoggerConfig config;
    private final ConfigManager configManager;
    @Getter
    private final Platform platform;
    @Getter
    private final Object placeholderOwner;
    @Getter @Setter
    private LanguageManager languageManager;

    @Getter @Setter
    private PrefixMode chatPrefixMode = PrefixMode.FULL;
    @Getter @Setter
    private PrefixMode consolePrefixMode = PrefixMode.SHORT;
    @Getter @Setter
    private String customPrefix = "[UAP]";

    @Getter @Setter
    private LogTarget defaultTarget = LogTarget.BOTH;
    @Getter @Setter
    private boolean consoleStripFormatting = false;
    @Getter @Setter
    private boolean consoleUseGradient = false;

    @Getter
    private final MiniMessage miniMessage = MiniMessage.builder().strict(false).build();
    private final Map<String, PrefixedLoggerCore> prefixedLoggers = new HashMap<>();
    @Getter
    private ExternalPlaceholderEngine externalPlaceholderEngine = ExternalPlaceholderEngine.NOOP;

    /**
     * Create a logger core instance.
     *
     * @param platform platform adapter
     * @param configManager config manager
     * @param placeholderOwner owner key for local placeholders
     * @param pluginName plugin/mod name for prefix defaults
     */
    public LoggerCore(Platform platform, ConfigManager configManager, Object placeholderOwner, String pluginName) {
        this.platform = platform;
        this.configManager = configManager;
        this.placeholderOwner = placeholderOwner != null ? placeholderOwner : this;
        String loggerDir = resolveLoggerDir(platform, pluginName);
        migrateLoggerConfigIfNeeded(loggerDir);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(LOGGER_DIR_PLACEHOLDER, loggerDir);
        this.config = configManager.register(LoggerConfig.class, placeholders);

        if (pluginName != null && !pluginName.isEmpty() && config.getPluginName().isEmpty()) {
            config.setPluginName(pluginName);
        }

        if (config.getShortName().isEmpty()) {
            config.setShortName(generateShortName(config.getPluginName()));
        }

        loadConfiguration();
        configManager.save(LoggerConfig.class);

        configManager.onChange(LoggerConfig.class, (cfg, sections) -> {
            config = cfg;
            loadConfiguration();
        });
    }

    public void setExternalPlaceholderEngine(ExternalPlaceholderEngine externalPlaceholderEngine) {
        this.externalPlaceholderEngine = externalPlaceholderEngine != null
                ? externalPlaceholderEngine
                : ExternalPlaceholderEngine.NOOP;
    }

    /**
     * Sets auto-localization state.
     *
     * @param enabled true to enable auto-localization
     */
    public void setAutoLocalization(boolean enabled) {
        if (config != null) {
            config.setAutoLocalization(enabled);
            configManager.save(LoggerConfig.class);
        }
    }

    public void reload() {
        if (config != null) {
            loadConfiguration();
        }
    }

    public LogBuilderCore log() {
        return new LogBuilderCore(this, LogLevel.INFO);
    }

    public LogBuilderCore noPrefix() {
        return new LogBuilderCore(this, LogLevel.INFO).noPrefix();
    }

    public LogBuilderCore info() {
        return new LogBuilderCore(this, LogLevel.INFO);
    }

    public LogBuilderCore warn() {
        return new LogBuilderCore(this, LogLevel.WARN);
    }

    public LogBuilderCore error() {
        return new LogBuilderCore(this, LogLevel.ERROR);
    }

    public LogBuilderCore debug() {
        return new LogBuilderCore(this, LogLevel.DEBUG);
    }

    public LogBuilderCore success() {
        return new LogBuilderCore(this, LogLevel.SUCCESS);
    }

    public PrefixedLoggerCore create(String name) {
        return withPrefix(name);
    }

    private String resolveLoggerDir(Platform platform, String pluginName) {
        if (platform instanceof ConfigNamespaceProvider provider) {
            String namespace = provider.resolveConfigNamespace(pluginName);
            return sanitizeLoggerDir(namespace);
        }
        return LOGGER_DIR_DEFAULT;
    }

    private String sanitizeLoggerDir(String namespace) {
        if (namespace == null) {
            return LOGGER_DIR_DEFAULT;
        }
        String trimmed = namespace.trim();
        if (trimmed.isEmpty()) {
            return LOGGER_DIR_DEFAULT;
        }
        StringBuilder result = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.') {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        String sanitized = result.toString();
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return LOGGER_DIR_DEFAULT;
        }
        return sanitized;
    }

    private void migrateLoggerConfigIfNeeded(String loggerDir) {
        if (LOGGER_DIR_DEFAULT.equals(loggerDir) || platform == null) {
            return;
        }
        Path baseDir = platform.configDir();
        if (baseDir == null) {
            return;
        }
        Path targetDir = baseDir.resolve(loggerDir);
        if (findExistingLoggerConfig(targetDir) != null) {
            return;
        }
        Path source = findExistingLoggerConfig(baseDir);
        if (source == null) {
            return;
        }
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(source.getFileName());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            platform.logger().info("Migrated logger config to " + target);
        } catch (Exception e) {
            platform.logger().warn("Failed to migrate logger config to " + targetDir, e);
        }
    }

    private Path findExistingLoggerConfig(Path baseDir) {
        for (String ext : LOGGER_EXTENSIONS) {
            Path candidate = baseDir.resolve(LOGGER_BASE_NAME + "." + ext);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public PrefixedLoggerCore create(String name, String prefix) {
        return withPrefix(name, prefix);
    }

    public PrefixedLoggerCore withPrefix(String name) {
        return withPrefix(name, "[" + name + "]");
    }

    public PrefixedLoggerCore withPrefix(String name, String prefix) {
        return prefixedLoggers.computeIfAbsent(name, key -> {
            PrefixedLoggerCore prefixedLogger = new PrefixedLoggerCore(this, name, prefix);

            if (config != null && config.addSubLogger(name)) {
                configManager.save(LoggerConfig.class);
            }

            if (config != null && config.getSubLoggers() != null) {
                SubLoggerConfig subLoggerConfig = config.getSubLoggers().get(name);
                if (subLoggerConfig != null) {
                    prefixedLogger.setEnabled(subLoggerConfig.isEnabled());
                }
            }

            return prefixedLogger;
        });
    }

    public void broadcast(Object message) {
        send(LogLevel.INFO, message, null, null, LogTarget.BOTH, true);
    }

    public void setPrefixedLoggerEnabled(String name, boolean enabled) {
        PrefixedLoggerCore logger = prefixedLoggers.get(name);
        if (logger != null) {
            logger.setEnabled(enabled);
        }
    }

    public Component parseMessage(Object message,
                                  LogLevel level,
                                  LogTarget target,
                                  @Nullable Audience directAudience,
                                  @Nullable Collection<? extends Audience> audienceCollection,
                                  Object... placeholdersArgs) {
        return LogMessageFormatter.format(this, message, level, target, null, directAudience, audienceCollection, placeholdersArgs);
    }

    public Component parseMessage(Object message,
                                  LogLevel level,
                                  LogTarget target,
                                  @Nullable PrefixMode prefixOverride,
                                  @Nullable Audience directAudience,
                                  @Nullable Collection<? extends Audience> audienceCollection,
                                  Object... placeholdersArgs) {
        return LogMessageFormatter.format(this, message, level, target, prefixOverride, directAudience, audienceCollection, placeholdersArgs);
    }

    public void send(LogLevel level,
                     Object message,
                     @Nullable Audience audience,
                     @Nullable Collection<? extends Audience> audiences,
                     LogTarget target,
                     boolean broadcast,
                     Object... placeholders) {
        Component component = parseMessage(message, level, target, audience, audiences, placeholders);
        Collection<Audience> recipients = LogDispatcher.determineRecipients(audience, audiences, broadcast, target, platform);
        LogDispatcher.deliver(platform, component, recipients, target);
    }

    @Override
    protected void send(LogLevel level, Object message) {
        send(level, message, null, null, defaultTarget, false);
    }

    @Override
    protected void send(LogLevel level, Object message, Audience player) {
        send(level, message, player, null, LogTarget.CHAT, false);
    }

    @Override
    protected void send(LogLevel level, Object message, Audience player, boolean all) {
        send(level, message, player, null, defaultTarget, all);
    }

    @Override
    protected void sendToConsole(LogLevel level, Object message) {
        send(level, message, null, null, LogTarget.CONSOLE, false);
    }

    @Override
    protected void sendToPlayers(LogLevel level, Object message, Collection<? extends Audience> players) {
        send(level, message, null, players, LogTarget.CHAT, false);
    }

    /**
     * Resolves colors for the specified log level directly from the active configuration.
     * Falls back to built-in defaults if the configuration isn't available yet.
     *
     * @param level log level being rendered
     * @param forConsole true to use console palette, false for chat palette
     * @return array of colors representing a gradient for the given level/target
     */
    public String[] resolveColorsForLevel(LogLevel level, boolean forConsole) {
        LoggerConfig cfg = config;
        if (cfg == null) {
            return LoggerConfig.defaultColors(level);
        }
        return cfg.resolveColors(level, forConsole);
    }

    private void loadConfiguration() {
        if (config == null) {
            return;
        }

        if (config.getPrefix() != null) {
            chatPrefixMode = config.getChatPrefixMode();
            consolePrefixMode = config.getConsolePrefixMode();
            customPrefix = config.getPrefix().getCustom();
            consoleUseGradient = config.getPrefix().isUseGradientConsole();
        }

        if (config.getDefaults() != null) {
            defaultTarget = config.getDefaultTarget();
        }

        if (config.getConsole() != null) {
            consoleStripFormatting = config.getConsole().isStripFormatting();
        }

        loadSubLoggers();
    }

    private void loadSubLoggers() {
        if (config == null || config.getSubLoggers() == null) {
            return;
        }

        for (Map.Entry<String, SubLoggerConfig> entry : config.getSubLoggers().entrySet()) {
            PrefixedLoggerCore existing = prefixedLoggers.get(entry.getKey());
            if (existing != null) {
                existing.setEnabled(entry.getValue().isEnabled());
            }
        }
    }

    private String generateShortName(String name) {
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
}
