package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.bungee.BungeePlatformProvider;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * BungeeCord-specific bootstrap helper for wiring MagicUtils services.
 */
public final class BungeeBootstrap {
    private BungeeBootstrap() {
    }

    /**
     * Creates a bootstrap builder for a Bungee plugin.
     *
     * @param plugin Bungee plugin instance
     * @param pluginName logical plugin name for logger/messages
     * @return bootstrap builder
     */
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
        private Path dataDirectory;
        private Logger jul;
        private Platform platform;
        private ConfigManager configManager;
        private LoggerCore logger;
        private LanguageManager languageManager;
        private String language = "en";
        private boolean initLanguage = true;
        private boolean bindLoggerLanguage = true;
        private boolean setMessagesManager = true;
        private boolean registerMessages = true;
        private boolean addMagicUtilsMessages = true;
        private Consumer<LanguageManager> translations;
        private boolean enableCommands;
        private String permissionPrefix;
        private Executor asyncExecutor;
        private Consumer<CommandRegistry> commandConfigurer;

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
        public Builder logger(Logger jul) {
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
         * Sets the custom logger core.
         *
         * @param logger logger core
         * @return this builder
         */
        public Builder loggerCore(LoggerCore logger) {
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
         * Sets the default language.
         *
         * @param language language code (e.g., "en")
         * @return this builder
         */
        public Builder language(String language) {
            if (language != null && !language.isBlank()) {
                this.language = language;
            }
            return this;
        }

        /**
         * Sets whether to initialize the language manager.
         *
         * @param initLanguage true to initialize
         * @return this builder
         */
        public Builder initLanguage(boolean initLanguage) {
            this.initLanguage = initLanguage;
            return this;
        }

        /**
         * Sets whether to bind the logger to the language manager.
         *
         * @param bindLoggerLanguage true to bind
         * @return this builder
         */
        public Builder bindLoggerLanguage(boolean bindLoggerLanguage) {
            this.bindLoggerLanguage = bindLoggerLanguage;
            return this;
        }

        /**
         * Sets whether to set the global messages manager.
         *
         * @param setMessagesManager true to set
         * @return this builder
         */
        public Builder setMessagesManager(boolean setMessagesManager) {
            this.setMessagesManager = setMessagesManager;
            return this;
        }

        /**
         * Sets whether to register messages for the plugin.
         *
         * @param registerMessages true to register
         * @return this builder
         */
        public Builder registerMessages(boolean registerMessages) {
            this.registerMessages = registerMessages;
            return this;
        }

        /**
         * Sets whether to add default MagicUtils messages.
         *
         * @param addMagicUtilsMessages true to add
         * @return this builder
         */
        public Builder addMagicUtilsMessages(boolean addMagicUtilsMessages) {
            this.addMagicUtilsMessages = addMagicUtilsMessages;
            return this;
        }

        /**
         * Sets the translations consumer for additional language setup.
         *
         * @param translations translations configurer
         * @return this builder
         */
        public Builder translations(Consumer<LanguageManager> translations) {
            this.translations = translations;
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
                            prepared.logger()
                    )
                    .languageManager(prepared.languageManager())
                    .manageConfigManager(configManager == null)
                    .component(Plugin.class, plugin)
                    .component(ProxyServer.class, proxy)
                    .build();

            if (prepared.commandRegistry() != null) {
                runtime.putComponent(CommandRegistry.class, prepared.commandRegistry());
                runtime.onClose("commandRegistry", () -> CommandRegistry.shutdown(plugin));
            }
            if (registerMessages) {
                runtime.onClose("messages.scope", () -> Messages.unregister(pluginName));
            }
            if (setMessagesManager) {
                runtime.onClose("messages.default", () -> {
                    if (Messages.getLanguageManager() == prepared.languageManager()) {
                        Messages.setLanguageManager(null);
                    }
                });
            }

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
            LoggerCore resolvedLogger = logger != null
                    ? logger
                    : new LoggerCore(resolvedPlatform, resolvedConfigManager, plugin, pluginName);
            LanguageManager resolvedLanguageManager = languageManager != null
                    ? languageManager
                    : new LanguageManager(resolvedPlatform, resolvedConfigManager);

            if (initLanguage) {
                resolvedLanguageManager.init(language);
            }
            if (translations != null) {
                translations.accept(resolvedLanguageManager);
            }
            if (addMagicUtilsMessages) {
                resolvedLanguageManager.addMagicUtilsMessages();
            }
            if (registerMessages) {
                Messages.register(pluginName, resolvedLanguageManager);
            }
            if (setMessagesManager) {
                Messages.setLanguageManager(resolvedLanguageManager);
            }
            if (bindLoggerLanguage) {
                resolvedLogger.setLanguageManager(resolvedLanguageManager);
            }

            CommandRegistry registry = null;
            if (enableCommands) {
                String prefix = permissionPrefix != null ? permissionPrefix : pluginName;
                registry = CommandRegistry.create(proxy, plugin, prefix, resolvedLogger, asyncExecutor);
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
                LoggerCore logger,
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
            LoggerCore logger,
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
            LoggerCore logger,
            LanguageManager languageManager,
            CommandRegistry commandRegistry
    ) {
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
