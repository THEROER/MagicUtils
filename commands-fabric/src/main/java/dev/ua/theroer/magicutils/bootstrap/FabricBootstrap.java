package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsSupport;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.fabric.FabricPlatformProvider;
import dev.ua.theroer.magicutils.platform.fabric.MagicUtilsFabricConsumerRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.server.MinecraftServer;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric-specific bootstrap helper for wiring MagicUtils services.
 */
public final class FabricBootstrap {
    private FabricBootstrap() {
    }

    /**
     * Creates a bootstrap builder for a Fabric mod.
     *
     * @param modName logical mod name for logger/messages/commands
     * @param serverSupplier server supplier used by the platform provider
     * @return bootstrap builder
     */
    public static Builder forMod(String modName, Supplier<MinecraftServer> serverSupplier) {
        return new Builder(modName, serverSupplier);
    }

    /**
     * Builder for wiring MagicUtils services on Fabric.
     */
    public static final class Builder {
        private final String modName;
        private final Supplier<MinecraftServer> serverSupplier;
        private org.slf4j.Logger slf4j;
        private Path configDir;
        private Platform platform;
        private ConfigManager configManager;
        private Logger logger;
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
        private int opLevel = 2;
        private Consumer<CommandRegistry> commandConfigurer;
        private boolean enableDiagnostics;
        private Consumer<DiagnosticRegistry> diagnosticsConfigurer;

        private Builder(String modName, Supplier<MinecraftServer> serverSupplier) {
            this.modName = normalizeModName(modName);
            this.serverSupplier = serverSupplier != null ? serverSupplier : () -> null;
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
         * Overrides the backing SLF4J logger.
         *
         * @param slf4j logger to use
         * @return builder
         */
        public Builder slf4j(org.slf4j.Logger slf4j) {
            this.slf4j = slf4j;
            return this;
        }

        /**
         * Overrides the config directory used by the platform adapter.
         *
         * @param configDir config directory
         * @return builder
         */
        public Builder configDir(Path configDir) {
            this.configDir = configDir;
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
         * Overrides the logger instance.
         *
         * @param logger logger to use
         * @return builder
         */
        public Builder logger(Logger logger) {
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
         * Toggles registering a scoped {@link Messages} manager for the mod.
         *
         * @param registerMessages true to register the mod scope
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
         * Overrides the op level treated as operator by the command registry.
         *
         * @param opLevel op level
         * @return builder
         */
        public Builder opLevel(int opLevel) {
            this.opLevel = opLevel;
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
                            prepared.logger().getCore()
                    )
                    .languageManager(prepared.languageManager())
                    .manageConfigManager(configManager == null)
                    .component(Logger.class, prepared.logger())
                    .build();

            if (bindClientLocaleSync) {
                runtime.manage("language.clientLocaleSync",
                        prepared.languageManager().bindClientLocaleSync(prepared.platform()));
            }

            if (prepared.commandRegistry() != null) {
                runtime.putComponent(CommandRegistry.class, prepared.commandRegistry());
                runtime.putNamedComponent("commandRegistry", prepared.commandRegistry());
                runtime.putNamedComponent("commandManager", prepared.commandRegistry().commandManager());
                runtime.onClose("commandRegistry", () -> CommandRegistry.shutdown(modName));
            }
            if (enableDiagnostics) {
                DiagnosticsSupport.install(runtime, diagnosticsConfigurer);
            }
            if (registerMessages) {
                runtime.onClose("messages.scope", () -> Messages.unregister(modName));
            }
            if (setMessagesManager) {
                runtime.onClose("messages.default", () -> {
                    if (Messages.getLanguageManager() == prepared.languageManager()) {
                        Messages.setLanguageManager(null);
                    }
                });
            }

            // Register this mod in the shared-runtime consumer registry so the
            // standalone bundle command can list it (/magicutils mods|mod <id>),
            // mirroring the Bukkit bundle's plugins/plugin sub-commands.
            registerSharedRuntimeConsumer(runtime, prepared.commandRegistry());
            runtime.onClose("magicutils.consumerRegistry",
                    () -> MagicUtilsFabricConsumerRegistry.unregister(modName));

            return new RuntimeResult(runtime, prepared.platform(), prepared.configManager(), prepared.logger(),
                    prepared.languageManager(), prepared.commandRegistry());
        }

        private void registerSharedRuntimeConsumer(MagicRuntime runtime, @Nullable CommandRegistry commandRegistry) {
            boolean commandsEnabled = commandRegistry != null;
            String prefix = commandsEnabled
                    ? (permissionPrefix != null ? permissionPrefix : modName)
                    : null;

            Optional<ModMetadata> metadata = FabricLoader.getInstance()
                    .getModContainer(modName)
                    .map(container -> container.getMetadata());
            String version = metadata.map(m -> m.getVersion().getFriendlyString()).orElse("unknown");
            String name = metadata.map(ModMetadata::getName).filter(s -> !s.isBlank()).orElse(modName);
            String description = metadata.map(ModMetadata::getDescription).filter(s -> !s.isBlank()).orElse(null);
            List<String> authors = new ArrayList<>();
            metadata.ifPresent(m -> {
                for (Person person : m.getAuthors()) {
                    authors.add(person.getName());
                }
            });

            // Static metadata is captured once; the dynamic state (command and
            // component counts, diagnostics, closed) is read live from the
            // runtime whenever /magicutils mods rebuilds a snapshot. This avoids
            // freezing counts at buildRuntime() time, before the consumer has
            // registered its commands (Fabric wires those on server start).
            var meta = new MagicUtilsConsumerRegistry.StaticMeta(
                    name, version,
                    // Fabric mods have no single "main class"; use the mod id.
                    modName, description, null, List.copyOf(authors),
                    MagicUtilsConsumerPayloads.platformTypeLabel(runtime),
                    commandsEnabled, prefix, Instant.now());
            var view = MagicUtilsConsumerViews.liveView(
                    runtime,
                    () -> commandsEnabled && commandRegistry.commandManager() != null
                            ? commandRegistry.commandManager().getAll().size()
                            : 0,
                    () -> runtime.findComponent(DiagnosticsService.class).isPresent());
            MagicUtilsFabricConsumerRegistry.register(meta, view);
        }

        private Prepared prepare() {
            Platform resolvedPlatform = platform != null
                    ? platform
                    : new FabricPlatformProvider(serverSupplier,
                    slf4j != null ? slf4j : LoggerFactory.getLogger(modName),
                    configDir);
            ConfigManager resolvedConfigManager = configManager != null
                    ? configManager
                    : new ConfigManager(resolvedPlatform);
            Logger resolvedLogger = logger != null
                    ? logger
                    : new Logger(resolvedPlatform, resolvedConfigManager, modName);
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
                Messages.register(modName, resolvedLanguageManager);
            }
            if (setMessagesManager) {
                Messages.setLanguageManager(resolvedLanguageManager);
            }
            if (bindLoggerLanguage) {
                resolvedLogger.setLanguageManager(resolvedLanguageManager);
            }

            CommandRegistry registry = null;
            if (enableCommands) {
                String prefix = permissionPrefix != null ? permissionPrefix : modName;
                registry = CommandRegistry.create(modName, prefix, resolvedLogger, opLevel);
                if (commandConfigurer != null) {
                    commandConfigurer.accept(registry);
                }
            }

            return new Prepared(resolvedPlatform, resolvedConfigManager, resolvedLogger, resolvedLanguageManager, registry);
        }

        private static String normalizeModName(String modName) {
            if (modName == null || modName.isBlank()) {
                throw new IllegalArgumentException("modName is blank");
            }
            return modName.trim();
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
     * Result of bootstrap wiring.
     *
     * @param platform platform adapter
     * @param configManager config manager
     * @param logger logger instance
     * @param languageManager language manager
     * @param commandRegistry command registry (nullable when disabled)
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
     * Runtime-aware bootstrap result.
     *
     * @param runtime managed runtime container
     * @param platform platform adapter
     * @param configManager config manager
     * @param logger logger instance
     * @param languageManager language manager
     * @param commandRegistry command registry (nullable when disabled)
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
         * Returns the legacy bootstrap view without the runtime wrapper.
         *
         * @return legacy result
         */
        public Result result() {
            return new Result(platform, configManager, logger, languageManager, commandRegistry);
        }
    }
}
