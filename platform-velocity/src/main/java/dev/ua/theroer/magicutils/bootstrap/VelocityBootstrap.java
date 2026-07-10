package dev.ua.theroer.magicutils.bootstrap;

import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.ua.theroer.magicutils.bootstrap.MagicUtilsConsumerPayloads;
import dev.ua.theroer.magicutils.bootstrap.MagicUtilsConsumerViews;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsSupport;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.messaging.MessagingService;
import dev.ua.theroer.magicutils.messaging.redis.RedisConfig;
import dev.ua.theroer.magicutils.messaging.velocity.VelocityMessagingSupport;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.velocity.VelocityMagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.velocity.VelocityPlatformProvider;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Velocity-specific bootstrap helper for wiring MagicUtils services.
 */
public final class VelocityBootstrap {
    private VelocityBootstrap() {
    }

    /**
     * Creates a bootstrap builder for a Velocity plugin.
     *
     * @param proxy Velocity proxy server
     * @param plugin plugin instance used for event registration
     * @param pluginName logical plugin name for logger/messages
     * @param dataDirectory plugin data directory
     * @return bootstrap builder
     */
    public static Builder forPlugin(ProxyServer proxy, Object plugin, String pluginName, Path dataDirectory) {
        return new Builder(proxy, plugin, pluginName, dataDirectory);
    }

    /**
     * Builder for wiring MagicUtils services on Velocity.
     */
    public static final class Builder {
        private final ProxyServer proxy;
        private final Object plugin;
        private final String pluginName;
        private Path dataDirectory;
        private Logger slf4j;
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
        private boolean bindClientLocaleSync = true;
        private Consumer<LanguageManager> translations;
        private boolean enableCommands;
        private String permissionPrefix;
        private Executor asyncExecutor;
        private Consumer<CommandRegistry> commandConfigurer;
        private boolean enableDiagnostics;
        private Consumer<DiagnosticRegistry> diagnosticsConfigurer;
        private boolean enableMessaging;
        private RedisConfig.Redis messagingRedis;
        private Consumer<MessagingService.Builder> messagingConfigurer;

        private Builder(ProxyServer proxy, Object plugin, String pluginName, Path dataDirectory) {
            this.proxy = Objects.requireNonNull(proxy, "proxy");
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.pluginName = normalizePluginName(pluginName);
            this.dataDirectory = dataDirectory;
        }

        /**
         * Overrides the platform adapter.
         *
         * @param platform custom platform adapter
         * @return builder
         */
        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        /**
         * Overrides the Velocity data directory.
         *
         * @param dataDirectory plugin data directory
         * @return builder
         */
        public Builder dataDirectory(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
            return this;
        }

        /**
         * Overrides the backing SLF4J logger.
         *
         * @param slf4j logger to use
         * @return builder
         */
        public Builder slf4j(Logger slf4j) {
            this.slf4j = slf4j;
            return this;
        }

        /**
         * Overrides the config manager instance.
         *
         * @param configManager config manager to use
         * @return builder
         */
        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        /**
         * Overrides the logger core instance.
         *
         * @param logger logger core to use
         * @return builder
         */
        public Builder logger(LoggerCore logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Overrides the language manager instance.
         *
         * @param languageManager language manager to use
         * @return builder
         */
        public Builder languageManager(LanguageManager languageManager) {
            this.languageManager = languageManager;
            return this;
        }

        /**
         * Sets the default language code to initialize.
         *
         * @param language language code
         * @return builder
         */
        public Builder language(String language) {
            if (language != null && !language.isBlank()) {
                this.language = language;
            }
            return this;
        }

        /**
         * Toggles automatic language initialization.
         *
         * @param initLanguage true to call {@link LanguageManager#init(String)}
         * @return builder
         */
        public Builder initLanguage(boolean initLanguage) {
            this.initLanguage = initLanguage;
            return this;
        }

        /**
         * Toggles binding the language manager to the logger.
         *
         * @param bindLoggerLanguage true to set the logger language manager
         * @return builder
         */
        public Builder bindLoggerLanguage(boolean bindLoggerLanguage) {
            this.bindLoggerLanguage = bindLoggerLanguage;
            return this;
        }

        /**
         * Toggles setting the global {@link Messages} language manager.
         *
         * @param setMessagesManager true to set the global manager
         * @return builder
         */
        public Builder setMessagesManager(boolean setMessagesManager) {
            this.setMessagesManager = setMessagesManager;
            return this;
        }

        /**
         * Toggles registering a scoped {@link Messages} manager for the plugin.
         *
         * @param registerMessages true to register the plugin scope
         * @return builder
         */
        public Builder registerMessages(boolean registerMessages) {
            this.registerMessages = registerMessages;
            return this;
        }

        /**
         * Toggles adding MagicUtils default language entries.
         *
         * @param addMagicUtilsMessages true to add bundled defaults
         * @return builder
         */
        public Builder addMagicUtilsMessages(boolean addMagicUtilsMessages) {
            this.addMagicUtilsMessages = addMagicUtilsMessages;
            return this;
        }

        /**
         * Toggles synchronizing player language from client locale updates.
         *
         * @param bindClientLocaleSync true to bind client locale synchronization
         * @return builder
         */
        public Builder bindClientLocaleSync(boolean bindClientLocaleSync) {
            this.bindClientLocaleSync = bindClientLocaleSync;
            return this;
        }

        /**
         * Registers plugin translations against the language manager.
         *
         * @param translations translation registrar
         * @return builder
         */
        public Builder translations(Consumer<LanguageManager> translations) {
            this.translations = translations;
            return this;
        }

        /**
         * Enables command registry creation.
         *
         * @return builder
         */
        public Builder enableCommands() {
            this.enableCommands = true;
            return this;
        }

        /**
         * Overrides the permission prefix used by commands.
         *
         * @param permissionPrefix permission prefix
         * @return builder
         */
        public Builder permissionPrefix(String permissionPrefix) {
            this.permissionPrefix = permissionPrefix;
            return this;
        }

        /**
         * Overrides the async executor used by the Velocity command registry.
         *
         * @param asyncExecutor async executor
         * @return builder
         */
        public Builder asyncExecutor(Executor asyncExecutor) {
            this.asyncExecutor = asyncExecutor;
            return this;
        }

        /**
         * Allows additional command registry configuration after creation.
         *
         * @param commandConfigurer registry callback
         * @return builder
         */
        public Builder configureCommands(Consumer<CommandRegistry> commandConfigurer) {
            this.commandConfigurer = commandConfigurer;
            return this;
        }

        /**
         * Enables runtime diagnostics service creation.
         *
         * @return builder
         */
        public Builder enableDiagnostics() {
            this.enableDiagnostics = true;
            return this;
        }

        /**
         * Allows configuring diagnostics checks before the service is exposed.
         *
         * @param diagnosticsConfigurer diagnostics registry callback
         * @return builder
         */
        public Builder configureDiagnostics(Consumer<DiagnosticRegistry> diagnosticsConfigurer) {
            this.diagnosticsConfigurer = diagnosticsConfigurer;
            return this;
        }

        /**
         * Enables cross-server messaging using the default plugin-messaging transport.
         *
         * @return builder
         */
        public Builder enableMessaging() {
            this.enableMessaging = true;
            return this;
        }

        /**
         * Supplies Redis settings for messaging; when enabled, Redis is used.
         *
         * @param redis redis settings
         * @return builder
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
         * @return builder
         */
        public Builder configureMessaging(Consumer<MessagingService.Builder> messagingConfigurer) {
            this.messagingConfigurer = messagingConfigurer;
            this.enableMessaging = true;
            return this;
        }

        /**
         * Builds the bootstrap result without exposing the runtime wrapper.
         *
         * @return bootstrap result
         */
        public Result build() {
            RuntimeResult runtimeResult = buildRuntime();
            return runtimeResult.result();
        }

        /**
         * Builds the bootstrap result and a managed {@link MagicRuntime}.
         *
         * @return runtime-aware bootstrap result
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
                    .component(ProxyServer.class, proxy)
                    .build();

            if (bindClientLocaleSync) {
                runtime.manage("language.clientLocaleSync",
                        prepared.languageManager().bindClientLocaleSync(prepared.platform()));
            }

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
                VelocityMessagingSupport.install(
                        runtime, proxy, plugin, pluginName, messagingRedis, messagingConfigurer);
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

            // Register this plugin in the shared-runtime consumer registry so the
            // standalone velocity-bundle command can list it
            // (/magicutils mods|mod <id>), mirroring the Bukkit/Fabric bundles. The
            // registry filters out the bundle itself by name ("MagicUtils"), so the
            // velocity-bundle registering through this same path is a no-op there.
            registerSharedRuntimeConsumer(runtime, prepared.commandRegistry());
            runtime.onClose("magicutils.consumerRegistry",
                    () -> VelocityMagicUtilsConsumerRegistry.unregister(pluginName));

            return new RuntimeResult(runtime, prepared.platform(), prepared.configManager(), prepared.logger(),
                    prepared.languageManager(), prepared.commandRegistry());
        }

        private void registerSharedRuntimeConsumer(MagicRuntime runtime, @Nullable CommandRegistry commandRegistry) {
            boolean commandsEnabled = commandRegistry != null;
            String prefix = commandsEnabled
                    ? (permissionPrefix != null ? permissionPrefix : pluginName)
                    : null;

            Optional<PluginDescription> description = proxy.getPluginManager()
                    .fromInstance(plugin)
                    .map(container -> container.getDescription());
            String version = description
                    .flatMap(PluginDescription::getVersion)
                    .orElse("unknown");
            String name = description
                    .flatMap(PluginDescription::getName)
                    .filter(s -> !s.isBlank())
                    .orElse(pluginName);
            String website = description
                    .flatMap(PluginDescription::getUrl)
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            String descriptionText = description
                    .flatMap(PluginDescription::getDescription)
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            String mainClass = description
                    .map(PluginDescription::getId)
                    .orElse(pluginName);
            List<String> authors = new ArrayList<>(
                    description.map(PluginDescription::getAuthors).orElse(List.of()));

            // Static metadata is captured once; the dynamic state (command and
            // component counts, diagnostics, closed) is read live from the runtime
            // whenever /magicutils mods rebuilds a snapshot, so counts are never
            // frozen at buildRuntime() time before the consumer wires everything.
            var meta = new MagicUtilsConsumerRegistry.StaticMeta(
                    name, version, mainClass, descriptionText, website, List.copyOf(authors),
                    MagicUtilsConsumerPayloads.platformTypeLabel(runtime),
                    commandsEnabled, prefix, Instant.now());
            var view = MagicUtilsConsumerViews.liveView(
                    runtime,
                    () -> commandsEnabled && commandRegistry.commandManager() != null
                            ? commandRegistry.commandManager().getAll().size()
                            : 0,
                    () -> runtime.findComponent(DiagnosticsService.class).isPresent());
            VelocityMagicUtilsConsumerRegistry.register(meta, view);
        }

        private Prepared prepare() {
            Platform resolvedPlatform = platform != null
                    ? platform
                    : new VelocityPlatformProvider(proxy, slf4j, dataDirectory, plugin);
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
     * Result of bootstrap wiring.
     *
     * @param platform platform adapter
     * @param configManager config manager
     * @param logger logger core
     * @param languageManager language manager
     * @param commandRegistry command registry (nullable when disabled)
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
     * Runtime-aware bootstrap result.
     *
     * @param runtime managed runtime container
     * @param platform platform adapter
     * @param configManager config manager
     * @param logger logger core
     * @param languageManager language manager
     * @param commandRegistry command registry (nullable when disabled)
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
         * Returns the diagnostics service when diagnostics were enabled.
         *
         * @return diagnostics service or null
         */
        public @Nullable DiagnosticsService diagnosticsService() {
            return runtime.findComponent(DiagnosticsService.class).orElse(null);
        }

        /**
         * Returns the legacy bootstrap view without the runtime wrapper.
         *
         * @return legacy result
         */
        public Result result() {
            return new Result(platform, configManager, logger, languageManager, commandRegistry);
        }
    }
}
