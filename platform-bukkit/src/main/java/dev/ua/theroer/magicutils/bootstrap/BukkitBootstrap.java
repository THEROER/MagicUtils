package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitPlatformProvider;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit-specific bootstrap helper for wiring MagicUtils in a plugin.
 */
public final class BukkitBootstrap {
    private BukkitBootstrap() {
    }

    /**
     * Create a bootstrap builder for the given plugin instance.
     *
     * @param plugin owning plugin instance
     * @return bootstrap builder
     */
    public static Builder forPlugin(JavaPlugin plugin) {
        return new Builder(plugin);
    }

    /**
     * Builder for wiring MagicUtils services on Bukkit.
     */
    public static final class Builder {
        private final JavaPlugin plugin;
        private Platform platform;
        private ConfigManager configManager;
        private Logger logger;
        private LanguageManager languageManager;
        private String language = "en";
        private boolean initLanguage = true;
        private boolean bindLoggerLanguage = true;
        private boolean setMessagesManager = true;
        private boolean addMagicUtilsMessages = true;
        private Consumer<LanguageManager> translations;
        private boolean enableCommands;
        private String permissionPrefix;
        private Consumer<CommandRegistry> commandConfigurer;

        private Builder(JavaPlugin plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
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
         * Sets the default language code to initialize.
         *
         * @param language language code (for example, "en")
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
         * @param initLanguage true to call LanguageManager.init
         * @return builder
         */
        public Builder initLanguage(boolean initLanguage) {
            this.initLanguage = initLanguage;
            return this;
        }

        /**
         * Toggles binding the language manager to the logger.
         *
         * @param bindLoggerLanguage true to set Logger language manager
         * @return builder
         */
        public Builder bindLoggerLanguage(boolean bindLoggerLanguage) {
            this.bindLoggerLanguage = bindLoggerLanguage;
            return this;
        }

        /**
         * Toggles setting the global Messages language manager.
         *
         * @param setMessagesManager true to call Messages.setLanguageManager
         * @return builder
         */
        public Builder setMessagesManager(boolean setMessagesManager) {
            this.setMessagesManager = setMessagesManager;
            return this;
        }

        /**
         * Toggles persisting MagicUtils default messages.
         *
         * @param addMagicUtilsMessages true to save MagicUtils defaults
         * @return builder
         */
        public Builder addMagicUtilsMessages(boolean addMagicUtilsMessages) {
            this.addMagicUtilsMessages = addMagicUtilsMessages;
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
         * @param permissionPrefix permission prefix to use
         * @return builder
         */
        public Builder permissionPrefix(String permissionPrefix) {
            this.permissionPrefix = permissionPrefix;
            return this;
        }

        /**
         * Allows configuring commands after registry creation.
         *
         * @param commandConfigurer registry callback
         * @return builder
         */
        public Builder configureCommands(Consumer<CommandRegistry> commandConfigurer) {
            this.commandConfigurer = commandConfigurer;
            return this;
        }

        /**
         * Builds the bootstrap result and wires requested services.
         *
         * @return bootstrap result
         */
        public Result build() {
            Platform resolvedPlatform = platform != null ? platform : new BukkitPlatformProvider(plugin);
            ConfigManager resolvedConfigManager = configManager != null
                    ? configManager
                    : new ConfigManager(resolvedPlatform);
            Logger resolvedLogger = logger != null
                    ? logger
                    : new Logger(resolvedPlatform, plugin, resolvedConfigManager);

            LanguageManager resolvedLanguageManager = languageManager != null
                    ? languageManager
                    : new LanguageManager(plugin, resolvedConfigManager);

            if (initLanguage) {
                resolvedLanguageManager.init(language);
            }

            if (translations != null) {
                translations.accept(resolvedLanguageManager);
            }

            if (addMagicUtilsMessages) {
                resolvedLanguageManager.addMagicUtilsMessages();
            }

            if (setMessagesManager) {
                Messages.setLanguageManager(resolvedLanguageManager);
            }

            if (bindLoggerLanguage) {
                resolvedLogger.setLanguageManager(resolvedLanguageManager);
            }

            CommandRegistry registry = null;
            if (enableCommands) {
                String prefix = permissionPrefix != null ? permissionPrefix : plugin.getName();
                registry = CommandRegistry.create(plugin, prefix, resolvedLogger);
                if (commandConfigurer != null) {
                    commandConfigurer.accept(registry);
                }
            }

            return new Result(resolvedPlatform, resolvedConfigManager, resolvedLogger,
                    resolvedLanguageManager, registry);
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
}
