package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsSupport;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.neoforge.NeoForgePlatformProvider;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge-specific bootstrap helper for wiring MagicUtils services.
 */
public final class NeoForgeBootstrap {
    private NeoForgeBootstrap() {
    }

    /**
     * Creates a bootstrap builder for a NeoForge mod.
     *
     * @param modName logical mod name for logger/messages/commands
     * @param serverSupplier server supplier used by the platform provider
     * @return bootstrap builder
     */
    public static Builder forMod(String modName, Supplier<MinecraftServer> serverSupplier) {
        return new Builder(modName, serverSupplier);
    }

    /**
     * Builder for wiring MagicUtils services on NeoForge.
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
         * Sets the platform provider.
         *
         * @param platform platform provider
         * @return this builder
         */
        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        /**
         * Sets the SLF4J logger.
         *
         * @param slf4j SLF4J logger
         * @return this builder
         */
        public Builder slf4j(org.slf4j.Logger slf4j) {
            this.slf4j = slf4j;
            return this;
        }

        /**
         * Sets the custom configuration directory.
         *
         * @param configDir config directory path
         * @return this builder
         */
        public Builder configDir(Path configDir) {
            this.configDir = configDir;
            return this;
        }

        /**
         * Sets the custom configuration manager.
         *
         * @param configManager config manager
         * @return this builder
         */
        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        /**
         * Sets the custom logger instance.
         *
         * @param logger logger instance
         * @return this builder
         */
        public Builder logger(Logger logger) {
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
         * @param language language code
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
         * Sets whether to register messages for the mod.
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
         * Toggles synchronizing player language from client locale updates.
         *
         * @param bindClientLocaleSync true to bind client locale synchronization
         * @return this builder
         */
        public Builder bindClientLocaleSync(boolean bindClientLocaleSync) {
            this.bindClientLocaleSync = bindClientLocaleSync;
            return this;
        }

        /**
         * Sets the translations configurer.
         *
         * @param translations translations configurer
         * @return this builder
         */
        public Builder translations(Consumer<LanguageManager> translations) {
            this.translations = translations;
            return this;
        }

        /**
         * Enables command support.
         *
         * @return this builder
         */
        public Builder enableCommands() {
            this.enableCommands = true;
            return this;
        }

        /**
         * Sets the permission prefix for commands.
         *
         * @param permissionPrefix permission prefix
         * @return this builder
         */
        public Builder permissionPrefix(String permissionPrefix) {
            this.permissionPrefix = permissionPrefix;
            return this;
        }

        /**
         * Sets the default OP level for command permissions.
         *
         * @param opLevel OP level (0-4)
         * @return this builder
         */
        public Builder opLevel(int opLevel) {
            this.opLevel = opLevel;
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

            // Publish this mod into the shared-runtime registry so the bundle's
            // `/magicutils mods` lists it (mirrors FabricBootstrap).
            registerSharedRuntimeConsumer(runtime, prepared.commandRegistry());
            runtime.onClose("sharedRuntimeConsumer",
                    () -> MagicUtilsConsumerRegistry.unregister(modName));

            return new RuntimeResult(runtime, prepared.platform(), prepared.configManager(), prepared.logger(),
                    prepared.languageManager(), prepared.commandRegistry());
        }

        private void registerSharedRuntimeConsumer(MagicRuntime runtime, @Nullable CommandRegistry commandRegistry) {
            boolean commandsEnabled = commandRegistry != null;
            int rootCommandCount = commandsEnabled && commandRegistry.commandManager() != null
                    ? commandRegistry.commandManager().getAll().size()
                    : 0;
            String prefix = commandsEnabled
                    ? (permissionPrefix != null ? permissionPrefix : modName)
                    : null;
            boolean diagnosticsEnabled = runtime.findComponent(DiagnosticsService.class).isPresent();

            // Look up the mod's metadata by its id. `modName` is the logical/display
            // name ("MagicUtils"); NeoForge keys containers by lowercase mod id
            // ("magicutils"), so try the id form first, then the raw name.
            // Reading IModInfo#getVersion can resolve a maven range against the game
            // version and throw "Game version not set" if FML isn't fully ready yet —
            // guard the whole read so it can never abort mod construction.
            Optional<IModInfo> modInfo = Optional.empty();
            String version = "unknown";
            String name = modName;
            String description = null;
            try {
                ModList modList = ModList.get();
                if (modList != null) {
                    Optional<? extends ModContainer> container =
                            modList.getModContainerById(modName.toLowerCase(Locale.ROOT));
                    if (container.isEmpty()) {
                        container = modList.getModContainerById(modName);
                    }
                    modInfo = container.map(ModContainer::getModInfo);
                }
                version = modInfo.map(m -> m.getVersion().toString()).orElse("unknown");
                name = modInfo.map(m -> m.getDisplayName()).filter(s -> !s.isBlank()).orElse(modName);
                description = modInfo.map(m -> m.getDescription()).filter(s -> !s.isBlank()).orElse(null);
            } catch (RuntimeException ex) {
                // FML not ready (e.g. called during mod construction) — fall back to
                // the logical name and let `/magicutils mods` show "unknown" version.
                version = "unknown";
                name = modName;
                description = null;
            }
            List<String> authors = new ArrayList<>();

            Map<String, Object> payload = MagicUtilsConsumerPayloads.runtimePayload(
                    runtime, name, version,
                    // NeoForge mods have no single "main class"; use the mod id.
                    modName, description, null, authors,
                    commandsEnabled, prefix, rootCommandCount, diagnosticsEnabled, Instant.now());
            MagicUtilsConsumerRegistry.register(
                    MagicUtilsConsumerInfo.fromPayload(payload, name, version, modName, description, null, authors));
        }

        private Prepared prepare() {
            Platform resolvedPlatform = platform != null
                    ? platform
                    : new NeoForgePlatformProvider(modName,
                    serverSupplier,
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
                registry = CommandRegistry.create(modName, prefix, resolvedLogger.getCore(), opLevel);
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
         * Converts this runtime result to a simple result.
         *
         * @return bootstrap result
         */
        public Result result() {
            return new Result(platform, configManager, logger, languageManager, commandRegistry);
        }
    }
}
