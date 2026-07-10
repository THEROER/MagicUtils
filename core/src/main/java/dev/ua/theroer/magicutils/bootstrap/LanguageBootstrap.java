package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Platform;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Shared language and {@link Messages} wiring for platform bootstrap helpers.
 *
 * <p>Historically every platform bootstrap (Bukkit, Bungee, Velocity, Fabric,
 * NeoForge) duplicated the same eight language flags, their setters, and the
 * apply/close logic. That logic only touches {@link LanguageManager},
 * {@link Messages}, {@link LoggerCore} and {@link Platform}, all of which are
 * visible from {@code core}, so it lives here as the single source of truth.
 * Platform builders own an instance and delegate their language setters to it;
 * command, diagnostics, and messaging wiring stay platform-specific because
 * those types differ per platform.
 *
 * <p>Terminology: a <em>scope</em> is the owner name a plugin/mod registers its
 * messages under (plugin name or mod id).
 */
public final class LanguageBootstrap {

    private String language = "en";
    private boolean initLanguage = true;
    private boolean bindLoggerLanguage = true;
    private boolean setMessagesManager = true;
    private boolean registerMessages = true;
    private boolean addMagicUtilsMessages = true;
    private boolean bindClientLocaleSync = true;
    private @Nullable Consumer<LanguageManager> translations;

    /**
     * Creates a language bootstrap with the recommended defaults already
     * applied (every flag on, language {@code "en"}).
     */
    public LanguageBootstrap() {
    }

    /**
     * Sets the default language code to initialize. Blank values are ignored.
     *
     * @param language language code (for example, {@code "en"})
     * @return this
     */
    public LanguageBootstrap language(String language) {
        if (language != null && !language.isBlank()) {
            this.language = language;
        }
        return this;
    }

    /**
     * Toggles automatic language initialization via
     * {@link LanguageManager#init(String)}.
     *
     * @param initLanguage true to initialize the language on build
     * @return this
     */
    public LanguageBootstrap initLanguage(boolean initLanguage) {
        this.initLanguage = initLanguage;
        return this;
    }

    /**
     * Toggles binding the language manager to the logger so log output is
     * localized.
     *
     * @param bindLoggerLanguage true to set the logger language manager
     * @return this
     */
    public LanguageBootstrap bindLoggerLanguage(boolean bindLoggerLanguage) {
        this.bindLoggerLanguage = bindLoggerLanguage;
        return this;
    }

    /**
     * Toggles setting the global {@link Messages} language manager to this
     * plugin/mod's manager.
     *
     * @param setMessagesManager true to set the global manager
     * @return this
     */
    public LanguageBootstrap setMessagesManager(boolean setMessagesManager) {
        this.setMessagesManager = setMessagesManager;
        return this;
    }

    /**
     * Toggles registering a scoped {@link Messages} manager keyed by the
     * plugin name or mod id.
     *
     * @param registerMessages true to register the scope
     * @return this
     */
    public LanguageBootstrap registerMessages(boolean registerMessages) {
        this.registerMessages = registerMessages;
        return this;
    }

    /**
     * Toggles adding the bundled MagicUtils default language entries.
     *
     * @param addMagicUtilsMessages true to add bundled defaults
     * @return this
     */
    public LanguageBootstrap addMagicUtilsMessages(boolean addMagicUtilsMessages) {
        this.addMagicUtilsMessages = addMagicUtilsMessages;
        return this;
    }

    /**
     * Toggles synchronizing a player's language from client locale updates.
     *
     * @param bindClientLocaleSync true to bind client locale synchronization
     * @return this
     */
    public LanguageBootstrap bindClientLocaleSync(boolean bindClientLocaleSync) {
        this.bindClientLocaleSync = bindClientLocaleSync;
        return this;
    }

    /**
     * Registers plugin/mod translations against the language manager on build.
     *
     * @param translations translation registrar
     * @return this
     */
    public LanguageBootstrap translations(@Nullable Consumer<LanguageManager> translations) {
        this.translations = translations;
        return this;
    }

    /**
     * Applies the recommended defaults: every flag enabled. This is already the
     * initial state, so it exists mainly to make intent explicit and to reset a
     * builder that called {@link #minimal()}.
     *
     * @return this
     */
    public LanguageBootstrap withRecommendedDefaults() {
        this.initLanguage = true;
        this.bindLoggerLanguage = true;
        this.setMessagesManager = true;
        this.registerMessages = true;
        this.addMagicUtilsMessages = true;
        this.bindClientLocaleSync = true;
        return this;
    }

    /**
     * Disables all automatic language wiring for callers that manage the
     * {@link LanguageManager} and {@link Messages} themselves. Leaves
     * {@link #language(String)} untouched.
     *
     * @return this
     */
    public LanguageBootstrap minimal() {
        this.initLanguage = false;
        this.bindLoggerLanguage = false;
        this.setMessagesManager = false;
        this.registerMessages = false;
        this.addMagicUtilsMessages = false;
        this.bindClientLocaleSync = false;
        return this;
    }

    /**
     * Whether client-locale synchronization should be bound on the runtime.
     *
     * @return true when enabled
     */
    public boolean bindsClientLocaleSync() {
        return bindClientLocaleSync;
    }

    /**
     * Whether a scoped messages registration was requested (drives the close
     * hook that unregisters it).
     *
     * @return true when enabled
     */
    public boolean registersMessages() {
        return registerMessages;
    }

    /**
     * Whether the global messages manager was set (drives the close hook that
     * clears it).
     *
     * @return true when enabled
     */
    public boolean setsMessagesManager() {
        return setMessagesManager;
    }

    /**
     * Runs the byte-identical language/messages wiring against a resolved
     * language manager and logger. Mirrors the former per-platform
     * {@code prepare()} bodies exactly.
     *
     * @param scope owner name (plugin name or mod id) used for the messages scope
     * @param languageManager resolved language manager
     * @param logger resolved logger core
     */
    public void apply(String scope, LanguageManager languageManager, LoggerCore logger) {
        if (initLanguage) {
            languageManager.init(language);
        }
        if (translations != null) {
            translations.accept(languageManager);
        }
        if (addMagicUtilsMessages) {
            languageManager.addMagicUtilsMessages();
        }
        if (registerMessages) {
            Messages.register(scope, languageManager);
        }
        if (setMessagesManager) {
            Messages.setLanguageManager(languageManager);
        }
        if (bindLoggerLanguage) {
            logger.setLanguageManager(languageManager);
        }
    }

    /**
     * Binds client-locale synchronization on the runtime when enabled. Mirrors
     * the former per-platform {@code buildRuntime()} block.
     *
     * @param runtime managed runtime
     * @param platform platform adapter
     * @param languageManager resolved language manager
     */
    public void bindClientLocaleSync(MagicRuntime runtime, Platform platform, LanguageManager languageManager) {
        if (bindClientLocaleSync) {
            runtime.manage("language.clientLocaleSync",
                    languageManager.bindClientLocaleSync(platform));
        }
    }

    /**
     * Installs the messages-related close hooks on the runtime when the
     * corresponding flags are enabled. Mirrors the former per-platform
     * {@code buildRuntime()} close blocks.
     *
     * @param runtime managed runtime
     * @param scope owner name (plugin name or mod id) used for the messages scope
     * @param languageManager resolved language manager
     */
    public void installMessagesCloseHooks(MagicRuntime runtime, String scope, LanguageManager languageManager) {
        if (registerMessages) {
            runtime.onClose("messages.scope", () -> Messages.unregister(scope));
        }
        if (setMessagesManager) {
            runtime.onClose("messages.default", () -> {
                if (Messages.getLanguageManager() == languageManager) {
                    Messages.setLanguageManager(null);
                }
            });
        }
    }
}
