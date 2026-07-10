package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsSupport;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.messaging.MessagingService;
import dev.ua.theroer.magicutils.messaging.bungee.BungeeMessagingSupport;
import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.bungee.BungeePlatformProvider;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * BungeeCord-specific bootstrap helper for wiring MagicUtils services.
 */
public final class BungeeBootstrap {
    private BungeeBootstrap() {
    }

    /**
     * Creates a bootstrap builder for a Bungee plugin, resolving the plugin
     * name from the plugin description.
     *
     * <p>This is the recommended entry point. The plugin name is taken from
     * {@code plugin.getDescription().getName()}; the data directory is the
     * plugin's data folder.
     *
     * @param plugin Bungee plugin instance
     * @return bootstrap builder
     */
    public static Builder forPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String pluginName = plugin.getDescription() != null ? plugin.getDescription().getName() : null;
        return new Builder(plugin, pluginName);
    }

    /**
     * Creates a bootstrap builder for a Bungee plugin with an explicit name.
     *
     * @param plugin Bungee plugin instance
     * @param pluginName logical plugin name for logger/messages
     * @return bootstrap builder
     * @deprecated prefer {@link #forPlugin(Plugin)}, which derives the name from
     *     the plugin description
     */
    @Deprecated
    public static Builder forPlugin(Plugin plugin, String pluginName) {
        return new Builder(plugin, pluginName);
    }

    /**
     * Builder for wiring MagicUtils services on BungeeCord.
     */
    public static final class Builder {
        private final Plugin plugin;
        private final ProxyServer proxy;
        private final String pluginName;
        private final LanguageBootstrap lang = new LanguageBootstrap();
        private Path dataDirectory;
        private java.util.logging.Logger jul;
        private Platform platform;
        private ConfigManager configManager;
        private Logger logger;
        private LanguageManager languageManager;
        private boolean enableCommands;
        private String permissionPrefix;
        private Executor asyncExecutor;
        private Consumer<CommandRegistry> commandConfigurer;
        private boolean enableDiagnostics;
        private Consumer<DiagnosticRegistry> diagnosticsConfigurer;
        private boolean enableMessaging;
        private RedisConfig.Redis messagingRedis;
        private Consumer<MessagingService.Builder> messagingConfigurer;

        private Builder(Plugin plugin, String pluginName) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.proxy = Objects.requireNonNull(plugin.getProxy(), "plugin.proxy");
            this.pluginName = normalizePluginName(pluginName);
            this.dataDirectory = plugin.getDataFolder() != null ? plugin.getDataFolder().toPath() : null;
        }

        /**
         * Sets the custom platform implementation.
         *
         * @param platform platform implementation
         * @return this builder
         */
        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        /**
         * Sets the custom data directory.
         *
         * @param dataDirectory data directory path
         * @return this builder
         */
        public Builder dataDirectory(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
            return this;
        }

        /**
         * Sets the backing JUL logger.
         *
         * @param jul logger instance
         * @return this builder
         */
        public Builder logger(java.util.logging.Logger jul) {
            this.jul = jul;
            return this;
        }

        /**
         * Sets the custom configuration manager.
         *
         * @param configManager configuration manager
         * @return this builder
         */
        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        /**
         * Sets the custom logger.
         *
         * @param logger logger to use
         * @return this builder
         */
        public Builder loggerCore(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets the custom language manager.
         *
         * @param languageManager language manager
         * @return this builder
         */
        public Builder languageManager(LanguageManager languageManager) {
            this.languageManager = languageManager;
            return this;
        }

        /**
         * Applies the recommended language defaults (every flag enabled). This
         * is already the initial state; call it to make intent explicit.
         *
         * @return this builder
         */
        public Builder withRecommendedDefaults() {
            lang.withRecommendedDefaults();
            return this;
        }

        /**
         * Disables all automatic language/messages wiring for callers that
         * manage localization themselves.
         *
         * @return this builder
         */
        public Builder minimal() {
            lang.minimal();
            return this;
        }

        /**
         * Sets the default language.
         *
         * @param language language code (e.g., "en")
         * @return this builder
         */
        public Builder language(String language) {
            lang.language(language);
            return this;
        }

        /**
         * Sets whether to initialize the language manager.
         *
         * @param initLanguage true to initialize
         * @return this builder
         */
        public Builder initLanguage(boolean initLanguage) {
            lang.initLanguage(initLanguage);
            return this;
        }

        /**
         * Sets whether to bind the logger to the language manager.
         *
         * @param bindLoggerLanguage true to bind
         * @return this builder
         */
        public Builder bindLoggerLanguage(boolean bindLoggerLanguage) {
            lang.bindLoggerLanguage(bindLoggerLanguage);
            return this;
        }

        /**
         * Sets whether to set the global messages manager.
         *
         * @param setMessagesManager true to set
         * @return this builder
         */
        public Builder setMessagesManager(boolean setMessagesManager) {
            lang.setMessagesManager(setMessagesManager);
            return this;
        }

        /**
         * Sets whether to register messages for the plugin.
         *
         * @param registerMessages true to register
         * @return this builder
         */
        public Builder registerMessages(boolean registerMessages) {
            lang.registerMessages(registerMessages);
            return this;
        }

        /**
         * Sets whether to add default MagicUtils messages.
         *
         * @param addMagicUtilsMessages true to add
         * @return this builder
         */
        public Builder addMagicUtilsMessages(boolean addMagicUtilsMessages) {
            lang.addMagicUtilsMessages(addMagicUtilsMessages);
            return this;
        }

        /**
         * Toggles synchronizing player language from client locale updates.
         *
         * @param bindClientLocaleSync true to bind client locale synchronization
         * @return this builder
         */
        public Builder bindClientLocaleSync(boolean bindClientLocaleSync) {
            lang.bindClientLocaleSync(bindClientLocaleSync);
            return this;
        }

        /**
         * Sets the translations consumer for additional language setup.
         *
         * @param translations translations configurer
         * @return this builder
         */
        public Builder translations(Consumer<LanguageManager> translations) {
            lang.translations(translations);
            return this;
        }

        /**
         * Enables command support for the plugin.
         *
         * @return this builder
         */
        public Builder enableCommands() {
            this.enableCommands = true;
            return this;
        }

        /**
         * Sets the permission prefix for registered commands.
         *
         * @param permissionPrefix permission prefix
         * @return this builder
         */
        public Builder permissionPrefix(String permissionPrefix) {
            this.permissionPrefix = permissionPrefix;
            return this;
        }

        /**
         * Sets the async executor for commands.
         *
         * @param asyncExecutor async executor
         * @return this builder
         */
        public Builder asyncExecutor(Executor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;
            return this;
        }

        /**
         * Sets the command registry configurer.
         *
         * @param commandConfigurer command configurer
         * @return this builder
         */
        public Builder configureCommands(Consumer<CommandRegistry> commandConfigurer) {
            this.commandConfigurer = commandConfigurer;
            return this;
        }

        /**
         * Enables runtime diagnostics service creation.
         *
         * @return this builder
         */
        public Builder enableDiagnostics() {
            this.enableDiagnostics = true;
            return this;
        }

        /**
         * Sets the diagnostics registry configurer.
         *
         * @param diagnosticsConfigurer diagnostics configurer
         * @return this builder
         */
        public Builder configureDiagnostics(Consumer<DiagnosticRegistry> diagnosticsConfigurer) {
            this.diagnosticsConfigurer = diagnosticsConfigurer;
            return this;
        }

        /**
         * Enables cross-server messaging using the default plugin-messaging transport.
         *
         * @return this builder
         */
        public Builder enableMessaging() {
            this.enableMessaging = true;
            return this;
        }

        /**
         * Supplies Redis settings for messaging; when enabled, Redis is used.
         *
         * @param redis redis settings
         * @return this builder
         */
        public Builder messagingRedis(RedisConfig.Redis redis) {
            this.messagingRedis = redis;
            this.enableMessaging = true;
            return this;
        }

        /**
         * Allows configuring the messaging service builder before it is built.
         *
         * @param messagingConfigurer messaging builder callback
         * @return this builder
         */
        public Builder configureMessaging(Consumer<MessagingService.Builder> messagingConfigurer) {
            this.messagingConfigurer = messagingConfigurer;
            this.enableMessaging = true;
            return this;
        }

        /**
         * Builds the bootstrap result.
         *
         * @return bootstrap result
         */
        public Result build() {
            RuntimeResult runtimeResult = buildRuntime();
            return runtimeResult.result();
        }

        /**
         * Builds the bootstrap result and starts the runtime.
         *
         * @return runtime result
         */
        public RuntimeResult buildRuntime() {
            Prepared prepared = prepare();
            MagicRuntime runtime = MagicRuntime.builder(
                            prepared.platform(),
                            prepared.configManager(),
                            prepared.logger().getCore()
                    )
                    .languageManager(prepared.languageManager())
                    .manageConfigManager(configManager == null)
                    .component(Plugin.class, plugin)
                    .component(ProxyServer.class, proxy)
                    .component(Logger.class, prepared.logger())
                    .build();

            lang.bindClientLocaleSync(runtime, prepared.platform(), prepared.languageManager());

            if (prepared.commandRegistry() != null) {
                runtime.putComponent(CommandRegistry.class, prepared.commandRegistry());
                runtime.putNamedComponent("commandRegistry", prepared.commandRegistry());
                runtime.putNamedComponent("commandManager", prepared.commandRegistry().commandManager());
                runtime.onClose("commandRegistry", () -> CommandRegistry.shutdown(plugin));
            }
            if (enableDiagnostics) {
                DiagnosticsSupport.install(runtime, diagnosticsConfigurer);
            }
            if (enableMessaging) {
                BungeeMessagingSupport.install(
                        runtime, proxy, plugin, pluginName, messagingRedis, messagingConfigurer);
            }
            lang.installMessagesCloseHooks(runtime, pluginName, prepared.languageManager());

            return new RuntimeResult(runtime, prepared.platform(), prepared.configManager(), prepared.logger(),
                    prepared.languageManager(), prepared.commandRegistry());
        }

        private Prepared prepare() {
            Platform resolvedPlatform = platform != null
                    ? platform
                    : new BungeePlatformProvider(proxy, jul != null ? jul : plugin.getLogger(), dataDirectory, plugin);
            ConfigManager resolvedConfigManager = configManager != null
                    ? configManager
                    : new ConfigManager(resolvedPlatform);
            Logger resolvedLogger = logger != null
                    ? logger
                    : new Logger(resolvedPlatform, resolvedConfigManager, plugin, pluginName);
            LanguageManager resolvedLanguageManager = languageManager != null
                    ? languageManager
                    : new LanguageManager(resolvedPlatform, resolvedConfigManager);

            lang.apply(pluginName, resolvedLanguageManager, resolvedLogger.getCore());

            CommandRegistry registry = null;
            if (enableCommands) {
                String prefix = permissionPrefix != null ? permissionPrefix : pluginName;
                registry = CommandRegistry.create(proxy, plugin, prefix, resolvedLogger.getCore(), asyncExecutor);
                if (commandConfigurer != null) {
                    commandConfigurer.accept(registry);
                }
            }

            return new Prepared(resolvedPlatform, resolvedConfigManager, resolvedLogger, resolvedLanguageManager, registry);
        }

        private static String normalizePluginName(String pluginName) {
            if (pluginName == null || pluginName.isBlank()) {
                throw new IllegalArgumentException("pluginName is blank");
            }
            return pluginName.trim();
        }

        private record Prepared(
                Platform platform,
                ConfigManager configManager,
                Logger logger,
                LanguageManager languageManager,
                CommandRegistry commandRegistry
        ) {
        }
    }

    /**
     * Result of Bungee bootstrap.
     *
     * @param platform platform provider
     * @param configManager configuration manager
     * @param logger logger core
     * @param languageManager language manager
     * @param commandRegistry command registry (nullable if disabled)
     */
    public record Result(
            Platform platform,
            ConfigManager configManager,
            Logger logger,
            LanguageManager languageManager,
            CommandRegistry commandRegistry
    ) {
    }

    /**
     * Result of Bungee bootstrap including the runtime instance.
     *
     * @param runtime magic runtime instance
     * @param platform platform provider
     * @param configManager configuration manager
     * @param logger logger core
     * @param languageManager language manager
     * @param commandRegistry command registry (nullable if disabled)
     */
    public record RuntimeResult(
            MagicRuntime runtime,
            Platform platform,
            ConfigManager configManager,
            Logger logger,
            LanguageManager languageManager,
            CommandRegistry commandRegistry
    ) {
        /**
         * Returns the diagnostics service when diagnostics were enabled.
         *
         * @return diagnostics service or null
         */
        public @Nullable DiagnosticsService diagnosticsService() {
            return runtime.findComponent(DiagnosticsService.class).orElse(null);
        }

        /**
         * Converts this runtime result to a simple result.
         *
         * @return bootstrap result
         */
        public Result result() {
            return new Result(platform, configManager, logger, languageManager, commandRegistry);
        }
    }
}
