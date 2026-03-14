package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.neoforge.NeoForgePlatformProvider;
import net.minecraft.server.MinecraftServer;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
        private Consumer<LanguageManager> translations;
        private boolean enableCommands;
        private String permissionPrefix;
        private int opLevel = 2;
        private Consumer<CommandRegistry> commandConfigurer;

        private Builder(String modName, Supplier<MinecraftServer> serverSupplier) {
            this.modName = normalizeModName(modName);
            this.serverSupplier = serverSupplier != null ? serverSupplier : () -> null;
        }

        public Builder platform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public Builder slf4j(org.slf4j.Logger slf4j) {
            this.slf4j = slf4j;
            return this;
        }

        public Builder configDir(Path configDir) {
            this.configDir = configDir;
            return this;
        }

        public Builder configManager(ConfigManager configManager) {
            this.configManager = configManager;
            return this;
        }

        public Builder logger(Logger logger) {
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

        public Builder opLevel(int opLevel) {
            this.opLevel = opLevel;
            return this;
        }

        public Builder configureCommands(Consumer<CommandRegistry> commandConfigurer) {
            this.commandConfigurer = commandConfigurer;
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

            if (prepared.commandRegistry() != null) {
                runtime.putComponent(CommandRegistry.class, prepared.commandRegistry());
                runtime.onClose("commandRegistry", () -> CommandRegistry.shutdown(modName));
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

            return new RuntimeResult(runtime, prepared.platform(), prepared.configManager(), prepared.logger(),
                    prepared.languageManager(), prepared.commandRegistry());
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
        public Result result() {
            return new Result(platform, configManager, logger, languageManager, commandRegistry);
        }
    }
}
