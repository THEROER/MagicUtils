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

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public Builder dataDirectory(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
            return this;
        }

        public Builder logger(Logger jul) {
            this.jul = jul;
            return this;
        }

        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        public Builder loggerCore(LoggerCore logger) {
            this.logger = logger;
            return this;
        }

        public Builder languageManager(LanguageManager languageManager) {
            this.languageManager = languageManager;
            return this;
        }

        public Builder language(String language) {
            if (language != null && !language.isBlank()) {
                this.language = language;
            }
            return this;
        }

        public Builder initLanguage(boolean initLanguage) {
            this.initLanguage = initLanguage;
            return this;
        }

        public Builder bindLoggerLanguage(boolean bindLoggerLanguage) {
            this.bindLoggerLanguage = bindLoggerLanguage;
            return this;
        }

        public Builder setMessagesManager(boolean setMessagesManager) {
            this.setMessagesManager = setMessagesManager;
            return this;
        }

        public Builder registerMessages(boolean registerMessages) {
            this.registerMessages = registerMessages;
            return this;
        }

        public Builder addMagicUtilsMessages(boolean addMagicUtilsMessages) {
            this.addMagicUtilsMessages = addMagicUtilsMessages;
            return this;
        }

        public Builder translations(Consumer<LanguageManager> translations) {
            this.translations = translations;
            return this;
        }

        public Builder enableCommands() {
            this.enableCommands = true;
            return this;
        }

        public Builder permissionPrefix(String permissionPrefix) {
            this.permissionPrefix = permissionPrefix;
            return this;
        }

        public Builder asyncExecutor(Executor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;
            return this;
        }

        public Builder configureCommands(Consumer<CommandRegistry> commandConfigurer) {
            this.commandConfigurer = commandConfigurer;
            return this;
        }

        public Result build() {
            RuntimeResult runtimeResult = buildRuntime();
            return runtimeResult.result();
        }

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

    public record Result(
            Platform platform,
            ConfigManager configManager,
            LoggerCore logger,
            LanguageManager languageManager,
            CommandRegistry commandRegistry
    ) {
    }

    public record RuntimeResult(
            MagicRuntime runtime,
            Platform platform,
            ConfigManager configManager,
            LoggerCore logger,
            LanguageManager languageManager,
            CommandRegistry commandRegistry
    ) {
        public Result result() {
            return new Result(platform, configManager, logger, languageManager, commandRegistry);
        }
    }
}
