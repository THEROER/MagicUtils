package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleType;
import dev.ua.theroer.magicutils.platform.PlayerLocale;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.Tasks;
import lombok.Getter;
import lombok.Setter;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform-agnostic language manager.
 */
public class LanguageManager {
    private final Platform platform;
    private final ConfigManager configManager;
    private final PlatformLogger logger;
    private final TaskScheduler scheduler;
    private final Map<String, LanguageConfig> loadedLanguages = new ConcurrentHashMap<>();
    private final Set<String> pendingLanguages = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private final Map<UUID, String> autoDetectedPlayerLanguages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> registeredTranslations = new ConcurrentHashMap<>();
    private final Set<String> loggedMissingMessages = ConcurrentHashMap.newKeySet();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> LANGUAGE_EXTENSIONS = Set.of("jsonc", "json", "yml", "yaml", "toml");
    @Getter
    private String currentLanguage = "en";
    private LanguageConfig currentConfig;
    private LanguageConfig fallbackConfig;
    private String fallbackLanguage = "en";
    @Getter @Setter
    private boolean logMissingMessages = true;

    /**
     * Create a language manager, resolving a {@link Platform} from the provided platform or legacy plugin instance.
     *
     * @param platformOrPlugin platform abstraction or Bukkit plugin
     * @param configManager config manager for loading language files
     */
    public LanguageManager(Object platformOrPlugin, ConfigManager configManager) {
        this(resolvePlatform(platformOrPlugin), configManager);
    }

    /**
     * Create a language manager bound to a specific platform.
     *
     * @param platform platform abstraction
     * @param configManager config manager for loading language files
     */
    public LanguageManager(Platform platform, ConfigManager configManager) {
        this.platform = platform;
        this.configManager = configManager;
        this.logger = platform.logger();
        this.scheduler = Tasks.scheduler(platform);
    }

    /**
     * Initialise language manager and load default language (plus fallback if different).
     *
     * @param defaultLanguage language code to load first
     */
    public void init(String defaultLanguage) {
        this.currentLanguage = defaultLanguage;
        this.loggedMissingMessages.clear();
        loadLanguageBlocking(currentLanguage);

        if (!currentLanguage.equals(fallbackLanguage)) {
            loadFallbackLanguageBlocking(fallbackLanguage);
        } else {
            fallbackConfig = currentConfig;
        }
    }

    /**
     * Load a language file into memory.
     *
     * @param languageCode language code to load
     * @return true if successfully loaded
     */
    public boolean loadLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        if (loadedLanguages.containsKey(languageCode)) {
            return true;
        }
        if (isBlockingSensitiveThread()) {
            warnIfMainThread("loadLanguage");
            scheduleLanguageLoad(languageCode);
            return false;
        }
        return loadLanguageBlocking(languageCode);
    }

    private boolean loadLanguageBlocking(String languageCode) {
        boolean existed = languageFileExists(languageCode);
        try {
            this.loggedMissingMessages.clear();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("lang", languageCode);

            LanguageConfig config = configManager.register(LanguageConfig.class, placeholders);
            boolean changed = initializeLanguageDefaults(config, languageCode, existed);
            changed |= applyRegisteredTranslations(config, languageCode);

            if (changed) {
                try {
                    configManager.save(config);
                } catch (Exception e) {
                    logger.warn("Failed to save language defaults for: " + languageCode, e);
                }
            }

            loadedLanguages.put(languageCode, config);

            if (languageCode.equals(currentLanguage)) {
                currentConfig = config;
            }

            if (languageCode.equals(fallbackLanguage)) {
                fallbackConfig = config;
            }

            logger.info("Loaded language: " + languageCode);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to load language: " + languageCode, e);
            return false;
        }
    }

    private void scheduleLanguageLoad(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return;
        }
        if (!pendingLanguages.add(languageCode)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> loadLanguageBlocking(languageCode), scheduler.io())
                .whenComplete((ok, error) -> pendingLanguages.remove(languageCode));
    }

    private boolean initializeLanguageDefaults(LanguageConfig config, String languageCode, boolean existed) {
        Map<String, Map<String, String>> translations = createTranslations(languageCode);
        if (translations.isEmpty()) {
            return false;
        }
        if (!existed) {
            config.applyTranslations(translations, true);
            return true;
        }
        return false;
    }

    private boolean applyRegisteredTranslations(LanguageConfig config, String languageCode) {
        if (config == null || languageCode == null || languageCode.isBlank()) {
            return false;
        }
        Map<String, String> translations = registeredTranslations.get(languageCode);
        if (translations == null || translations.isEmpty()) {
            return false;
        }

        boolean changed = false;
        Map<String, String> customMessages = config.getCustomMessages();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || customMessages.containsKey(key)) {
                continue;
            }
            customMessages.put(key, value);
            changed = true;
        }
        return changed;
    }

    private void loadFallbackLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return;
        }
        if (loadedLanguages.containsKey(languageCode)) {
            applyFallback(languageCode);
            return;
        }
        if (isBlockingSensitiveThread()) {
            warnIfMainThread("loadLanguage");
            loadLanguageAsync(languageCode).thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    runOnMain(() -> applyFallback(languageCode), "apply fallback language " + languageCode);
                }
            });
            return;
        }
        if (loadLanguageBlocking(languageCode)) {
            applyFallback(languageCode);
        }
    }

    private void loadFallbackLanguageBlocking(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return;
        }
        if (loadLanguageBlocking(languageCode)) {
            applyFallback(languageCode);
        }
    }

    /**
     * Switch the current language for the manager.
     *
     * @param languageCode language code to set
     * @return true if language is now active
     */
    public boolean setLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        if (!loadedLanguages.containsKey(languageCode)) {
            if (isBlockingSensitiveThread()) {
                warnIfMainThread("loadLanguage");
                CompletableFuture.supplyAsync(() -> loadLanguageBlocking(languageCode), scheduler.io())
                        .thenAccept(success -> {
                            if (Boolean.TRUE.equals(success)) {
                                runOnMain(() -> applyLanguage(languageCode), "apply language " + languageCode);
                            }
                        });
                return false;
            }
            if (!loadLanguageBlocking(languageCode)) {
                return false;
            }
        }

        applyLanguage(languageCode);
        return true;
    }

    /**
     * Add or update a custom message for a specific language (or current language when null).
     * Saves the language file immediately.
     *
     * @param languageCode target language or null for current
     * @param key message key
     * @param value message text (null removes the key)
     */
    public void putCustomMessage(String languageCode, String key, String value) {
        String code = languageCode != null ? languageCode : currentLanguage;
        LanguageConfig cfg = loadedLanguages.get(code);
        if (cfg == null) {
            return;
        }
        cfg.putCustomMessage(key, value);
        try {
            configManager.save(cfg);
        } catch (Exception e) {
            logger.warn("Failed to save custom message '" + key + "' for language " + code, e);
        }
    }

    /**
     * Register bundled plugin translations for multiple languages.
     * Registered values are merged into already-loaded languages immediately and
     * applied automatically to any language loaded later.
     *
     * Existing custom message values are preserved to avoid overwriting user
     * overrides from disk.
     *
     * @param translationsByLanguage language -> (message key -> message text)
     */
    public void registerTranslations(Map<String, Map<String, String>> translationsByLanguage) {
        if (translationsByLanguage == null || translationsByLanguage.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, String>> entry : translationsByLanguage.entrySet()) {
            registerTranslations(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Register bundled plugin translations for a single language.
     * Registered values are merged into already-loaded languages immediately and
     * applied automatically to any language loaded later.
     *
     * Existing custom message values are preserved to avoid overwriting user
     * overrides from disk.
     *
     * @param languageCode language code to attach translations to
     * @param translations message key -> message text
     */
    public void registerTranslations(String languageCode, Map<String, String> translations) {
        if (languageCode == null || languageCode.isBlank() || translations == null || translations.isEmpty()) {
            return;
        }

        String normalizedLanguage = languageCode.trim();
        Map<String, String> normalizedTranslations = normalizeTranslations(translations);
        if (normalizedTranslations.isEmpty()) {
            return;
        }

        registeredTranslations.compute(normalizedLanguage, (ignored, existing) -> {
            Map<String, String> merged = new LinkedHashMap<>();
            if (existing != null && !existing.isEmpty()) {
                merged.putAll(existing);
            }
            merged.putAll(normalizedTranslations);
            return merged;
        });

        LanguageConfig config = loadedLanguages.get(normalizedLanguage);
        if (config != null && applyRegisteredTranslations(config, normalizedLanguage)) {
            try {
                configManager.save(config);
            } catch (Exception e) {
                logger.warn("Failed to save registered translations for: " + normalizedLanguage, e);
            }
        }
    }

    /**
     * Set fallback language used when a key is missing in the current language.
     *
     * @param languageCode fallback language code
     */
    public void setFallbackLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return;
        }

        this.fallbackLanguage = languageCode;
        if (!languageCode.equals(currentLanguage)) {
            loadFallbackLanguage(languageCode);
        } else {
            fallbackConfig = currentConfig;
        }
    }

    /**
     * List languages available on disk or already loaded.
     *
     * @return set of language codes found on disk or loaded in memory
     */
    public Set<String> getAvailableLanguages() {
        Set<String> languages = new HashSet<>();
        File langDir = platform.configDir().resolve("lang").toFile();

        if (langDir.exists() && langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> {
                if (name == null) {
                    return false;
                }
                int dot = name.lastIndexOf('.');
                if (dot <= 0 || dot >= name.length() - 1) {
                    return false;
                }
                String ext = name.substring(dot + 1).toLowerCase();
                return LANGUAGE_EXTENSIONS.contains(ext);
            });
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        languages.add(name.substring(0, dot));
                    }
                }
            }
        }

        languages.addAll(loadedLanguages.keySet());
        return languages;
    }

    /**
     * Get metadata for every available language.
     *
     * @return metadata for each available language
     */
    public Map<String, LanguageInfo> getLanguageInfos() {
        Map<String, LanguageInfo> infos = new HashMap<>();

        for (String code : getAvailableLanguages()) {
            if (!loadedLanguages.containsKey(code)) {
                loadLanguage(code);
            }

            LanguageConfig config = loadedLanguages.get(code);
            if (config != null) {
                infos.put(code, new LanguageInfo(
                        code,
                        config.getMetadata().getName(),
                        config.getMetadata().getAuthor(),
                        config.getMetadata().getVersion()));
            }
        }

        return infos;
    }

    /**
     * Resolve a message in the current language.
     *
     * @param key message key
     * @return resolved message or key if missing
     */
    public String getMessage(String key) {
        return resolveMessage(currentLanguage, key);
    }

    /**
     * Resolve a message with named placeholders.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return message with substitutions applied
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = resolveMessage(currentLanguage, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolve a message with positional replacements (name/value pairs).
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return message with substitutions applied
     */
    public String getMessage(String key, String... replacements) {
        String message = resolveMessage(currentLanguage, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Check whether the current language (or fallback) has a message for the key.
     *
     * @param key message key
     * @return true if present
     */
    public boolean hasMessage(String key) {
        if (hasMessage(currentConfig, key)) {
            return true;
        }
        ensureFallbackLoaded();
        return hasMessage(fallbackConfig, key);
    }

    /**
     * Check whether a specific language (or fallback) has a message for the key.
     *
     * @param languageCode language to inspect
     * @param key message key
     * @return true if present
     */
    public boolean hasMessageForLanguage(String languageCode, String key) {
        LanguageConfig primary = getOrLoadLanguage(languageCode);
        if (hasMessage(primary, key)) {
            return true;
        }
        ensureFallbackLoaded();
        return hasMessage(fallbackConfig, key);
    }

    /**
     * Resolve a message for a specific language code.
     *
     * @param languageCode language to use
     * @param key message key
     * @return resolved message or key if missing
     */
    public String getMessageForLanguage(String languageCode, String key) {
        return resolveMessage(languageCode, key);
    }

    /**
     * Resolve a message with placeholders for a specific language code.
     *
     * @param languageCode language to use
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message
     */
    public String getMessageForLanguage(String languageCode, String key, Map<String, String> placeholders) {
        String message = resolveMessage(languageCode, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolve a message with placeholders safely escaped for MiniMessage parsing.
     *
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message with escaped placeholder values
     */
    public String getMessageEscaped(String key, Map<String, String> placeholders) {
        String message = resolveMessage(currentLanguage, key);
        return applyPlaceholders(message, placeholders, true);
    }

    /**
     * Resolve a message with positional replacements escaped for MiniMessage parsing.
     *
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message with escaped replacement values
     */
    public String getMessageEscaped(String key, String... replacements) {
        String message = resolveMessage(currentLanguage, key);
        return applyReplacements(message, true, replacements);
    }

    /**
     * Resolve a message with positional replacements for a specific language code.
     *
     * @param languageCode language to use
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message
     */
    public String getMessageForLanguage(String languageCode, String key, String... replacements) {
        String message = resolveMessage(languageCode, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Resolve a message with placeholders for a specific language code, escaping values for MiniMessage.
     *
     * @param languageCode language to use
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message with escaped placeholder values
     */
    public String getMessageForLanguageEscaped(String languageCode, String key, Map<String, String> placeholders) {
        String message = resolveMessage(languageCode, key);
        return applyPlaceholders(message, placeholders, true);
    }

    /**
     * Resolve a message with replacements for a specific language code, escaping values for MiniMessage.
     *
     * @param languageCode language to use
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message with escaped replacement values
     */
    public String getMessageForLanguageEscaped(String languageCode, String key, String... replacements) {
        String message = resolveMessage(languageCode, key);
        return applyReplacements(message, true, replacements);
    }

    /**
     * Assign a preferred language for a player.
     *
     * @param playerId player UUID
     * @param languageCode language code to set (null/empty clears preference)
     * @return true if updated
     */
    public boolean setPlayerLanguage(UUID playerId, String languageCode) {
        return setStoredPlayerLanguage(playerLanguages, playerId, languageCode, false);
    }

    /**
     * Assign a preferred language for an {@link Audience}.
     *
     * @param audience audience with id
     * @param languageCode language code to set
     * @return true if updated
     */
    public boolean setPlayerLanguage(Audience audience, String languageCode) {
        return audience != null && setPlayerLanguage(audience.id(), languageCode);
    }

    /**
     * Assign a preferred language for a player-like object (expects getUniqueId()).
     *
     * @param player player object
     * @param languageCode language code to set
     * @return true if updated
     */
    public boolean setPlayerLanguage(Object player, String languageCode) {
        UUID id = extractUuid(player);
        return setPlayerLanguage(id, languageCode);
    }

    /**
     * Remove stored language preference for a player.
     *
     * @param playerId player UUID
     */
    public void clearPlayerLanguage(UUID playerId) {
        if (playerId != null) {
            playerLanguages.remove(playerId);
        }
    }

    /**
     * Assign an auto-detected language for a player based on client locale data.
     *
     * <p>Auto-detected languages are used only when no explicit player language override exists.</p>
     *
     * @param playerId player UUID
     * @param languageCode locale tag or language code to resolve
     * @return true if a supported language was stored or cleared
     */
    public boolean setAutoDetectedPlayerLanguage(UUID playerId, String languageCode) {
        return setStoredPlayerLanguage(autoDetectedPlayerLanguages, playerId, languageCode, true);
    }

    /**
     * Assign an auto-detected language for an {@link Audience}.
     *
     * @param audience audience with id
     * @param languageCode locale tag or language code to resolve
     * @return true if updated
     */
    public boolean setAutoDetectedPlayerLanguage(Audience audience, String languageCode) {
        return audience != null && setAutoDetectedPlayerLanguage(audience.id(), languageCode);
    }

    /**
     * Remove stored auto-detected language for a player.
     *
     * @param playerId player UUID
     */
    public void clearAutoDetectedPlayerLanguage(UUID playerId) {
        if (playerId != null) {
            autoDetectedPlayerLanguages.remove(playerId);
        }
    }

    /**
     * Remove stored auto-detected language for an arbitrary player object.
     *
     * @param player player object
     */
    public void clearAutoDetectedPlayerLanguage(Object player) {
        UUID id = extractUuid(player);
        clearAutoDetectedPlayerLanguage(id);
    }

    /**
     * Remove stored language preference for an arbitrary player object.
     *
     * @param player player object
     */
    public void clearPlayerLanguage(Object player) {
        UUID id = extractUuid(player);
        clearPlayerLanguage(id);
    }

    /**
     * Get stored language for a player or current language if none.
     *
     * @param playerId player UUID
     * @return language code
     */
    public String getPlayerLanguage(UUID playerId) {
        if (playerId == null) {
            return currentLanguage;
        }
        String explicit = playerLanguages.get(playerId);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String autoDetected = autoDetectedPlayerLanguages.get(playerId);
        if (autoDetected != null && !autoDetected.isBlank()) {
            return autoDetected;
        }
        return currentLanguage;
    }

    /**
     * Get stored language for a player-like object.
     *
     * @param player player object
     * @return language code
     */
    public String getPlayerLanguage(Object player) {
        UUID id = extractUuid(player);
        return id != null ? getPlayerLanguage(id) : currentLanguage;
    }

    /**
     * Snapshot of stored player language preferences.
     *
     * @return snapshot of player language preferences
     */
    public Map<UUID, String> getPlayerLanguages() {
        return Collections.unmodifiableMap(new HashMap<>(playerLanguages));
    }

    /**
     * Snapshot of auto-detected player language preferences.
     *
     * @return snapshot of auto-detected player language preferences
     */
    public Map<UUID, String> getAutoDetectedPlayerLanguages() {
        return Collections.unmodifiableMap(new HashMap<>(autoDetectedPlayerLanguages));
    }

    /**
     * Resolve a supported language code from a client locale tag or language code.
     *
     * @param languageCode client locale tag or language code
     * @return supported language code, or null when no supported match exists
     */
    public String resolveSupportedLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return null;
        }
        for (String candidate : expandLanguageCandidates(languageCode)) {
            String matched = matchAvailableLanguageCode(candidate);
            if (matched != null && !matched.isBlank()) {
                return matched;
            }
        }
        return null;
    }

    /**
     * Binds automatic player locale synchronization to the manager platform.
     *
     * @return subscription that removes the registered listeners
     */
    public ListenerSubscription bindClientLocaleSync() {
        return bindClientLocaleSync(platform);
    }

    /**
     * Binds automatic player locale synchronization to the provided platform.
     *
     * @param targetPlatform platform to subscribe to
     * @return subscription that removes the registered listeners
     */
    public ListenerSubscription bindClientLocaleSync(Platform targetPlatform) {
        Platform resolvedPlatform = targetPlatform != null ? targetPlatform : platform;
        if (resolvedPlatform == null) {
            return ListenerSubscription.noop();
        }
        ListenerSubscription localeSubscription = resolvedPlatform.subscribePlayerLocales(this::applyPlayerLocale);
        ListenerSubscription lifecycleSubscription = resolvedPlatform.subscribePlayerLifecycle(lifecycle -> {
            if (lifecycle != null && lifecycle.type() == PlayerLifecycleType.LEAVE) {
                clearAutoDetectedPlayerLanguage(lifecycle.playerId());
            }
        });
        return () -> {
            lifecycleSubscription.close();
            localeSubscription.close();
        };
    }

    /**
     * Resolve a message for an audience respecting per-player language overrides.
     *
     * @param audience target audience
     * @param key message key
     * @return resolved message
     */
    public String getMessageForAudience(Audience audience, String key) {
        if (audience == null || audience.id() == null) {
            return getMessage(key);
        }
        return resolveMessage(getPlayerLanguage(audience.id()), key);
    }

    /**
     * Resolve a message for an audience with placeholders.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message
     */
    public String getMessageForAudience(Audience audience, String key, Map<String, String> placeholders) {
        String message = getMessageForAudience(audience, key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Resolve a message for an audience with positional replacements.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message
     */
    public String getMessageForAudience(Audience audience, String key, String... replacements) {
        String message = getMessageForAudience(audience, key);
        return applyReplacements(message, replacements);
    }

    /**
     * Resolve a message for an audience with placeholders escaped for MiniMessage.
     *
     * @param audience target audience
     * @param key message key
     * @param placeholders placeholder map
     * @return resolved message with escaped placeholder values
     */
    public String getMessageForAudienceEscaped(Audience audience, String key, Map<String, String> placeholders) {
        String message = getMessageForAudience(audience, key);
        return applyPlaceholders(message, placeholders, true);
    }

    /**
     * Resolve a message for an audience with replacements escaped for MiniMessage.
     *
     * @param audience target audience
     * @param key message key
     * @param replacements placeholder/value pairs
     * @return resolved message with escaped replacement values
     */
    public String getMessageForAudienceEscaped(Audience audience, String key, String... replacements) {
        String message = getMessageForAudience(audience, key);
        return applyReplacements(message, true, replacements);
    }

    /**
     * Reload all loaded languages from disk.
     */
    public void reload() {
        warnIfMainThread("reload");
        loggedMissingMessages.clear();
        Map<String, LanguageConfig> snapshot = new HashMap<>(loadedLanguages);

        for (Map.Entry<String, LanguageConfig> entry : snapshot.entrySet()) {
            configManager.reload(entry.getValue());
        }

        currentConfig = loadedLanguages.get(currentLanguage);
        if (currentConfig == null) {
            loadLanguageBlocking(currentLanguage);
            currentConfig = loadedLanguages.get(currentLanguage);
        }

        fallbackConfig = loadedLanguages.get(fallbackLanguage);
        if (fallbackConfig == null && fallbackLanguage != null) {
            loadFallbackLanguageBlocking(fallbackLanguage);
        }
    }

    /**
     * Persist custom messages into the language file.
     *
     * @param languageCode language to modify
     * @param customMessages map of keys to values to persist
     */
    public void saveCustomMessages(String languageCode, Map<String, String> customMessages) {
        try {
            LanguageConfig config = loadedLanguages.get(languageCode);
            if (config == null) {
                loadLanguage(languageCode);
                config = loadedLanguages.get(languageCode);
            }

            if (config != null) {
                config.getCustomMessages().putAll(customMessages);
                configManager.save(config);
            }

        } catch (Exception e) {
            logger.warn("Failed to save language messages for: " + languageCode, e);
        }
    }

    /**
     * Persist currently loaded MagicUtils messages (ensures defaults exist).
     */
    public void addMagicUtilsMessages() {
        for (LanguageConfig config : loadedLanguages.values()) {
            configManager.save(config);
        }
    }

    /**
     * Load a language asynchronously to avoid blocking the main thread.
     *
     * @param languageCode language code to load
     * @return future that completes with true if loaded successfully
     */
    public CompletableFuture<Boolean> loadLanguageAsync(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (loadedLanguages.containsKey(languageCode)) {
            return CompletableFuture.completedFuture(true);
        }
        if (!pendingLanguages.add(languageCode)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> loadLanguageBlocking(languageCode), scheduler.io())
                .whenComplete((ok, error) -> pendingLanguages.remove(languageCode));
    }

    /**
     * Load a language and return a future that completes when loading finishes.
     *
     * @param languageCode language code to load
     * @return future that completes with true if loaded successfully
     */
    public CompletableFuture<Boolean> loadLanguageSmart(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (loadedLanguages.containsKey(languageCode)) {
            return CompletableFuture.completedFuture(true);
        }
        if (pendingLanguages.contains(languageCode)) {
            return CompletableFuture.completedFuture(false);
        }
        if (isBlockingSensitiveThread()) {
            return loadLanguageAsync(languageCode);
        }
        return CompletableFuture.completedFuture(loadLanguageBlocking(languageCode));
    }

    /**
     * Switch active language asynchronously.
     *
     * @param languageCode language code
     * @return future that completes with true if active language is set
     */
    public CompletableFuture<Boolean> setLanguageAsync(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return loadLanguageAsync(languageCode).thenCompose(success -> {
            if (!Boolean.TRUE.equals(success)) {
                return CompletableFuture.completedFuture(false);
            }
            return Tasks.runOnMain(platform, () -> applyLanguage(languageCode))
                    .handle((ignored, error) -> {
                        if (error != null) {
                            logger.warn("Failed to apply language on main thread: " + languageCode, error);
                            return false;
                        }
                        return true;
                    });
        });
    }

    /**
     * Switch active language, auto-switching to async when on sensitive threads.
     *
     * @param languageCode language code
     * @return future that completes with true if active language is set
     */
    public CompletableFuture<Boolean> setLanguageSmart(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (isBlockingSensitiveThread()) {
            return setLanguageAsync(languageCode);
        }
        return CompletableFuture.completedFuture(setLanguage(languageCode));
    }

    /**
     * Reload all loaded languages asynchronously.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAsync() {
        return CompletableFuture.runAsync(this::reload, scheduler.io());
    }

    /**
     * Reload all loaded languages, auto-switching to async on sensitive threads.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadSmart() {
        if (isBlockingSensitiveThread()) {
            return reloadAsync();
        }
        reload();
        return CompletableFuture.completedFuture(null);
    }

    private LanguageConfig getOrLoadLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return currentConfig;
        }

        LanguageConfig config = loadedLanguages.get(languageCode);
        if (config == null) {
            if (isBlockingSensitiveThread()) {
                scheduleLanguageLoad(languageCode);
                return null;
            }
            if (loadLanguageBlocking(languageCode)) {
                config = loadedLanguages.get(languageCode);
            }
        }
        return config;
    }

    private boolean setStoredPlayerLanguage(Map<UUID, String> store,
                                            UUID playerId,
                                            String languageCode,
                                            boolean resolveSupportedLanguage) {
        if (playerId == null) {
            return false;
        }

        if (languageCode == null || languageCode.isBlank()) {
            store.remove(playerId);
            return true;
        }

        String resolvedLanguageCode = resolveSupportedLanguage
                ? resolveSupportedLanguageCode(languageCode)
                : languageCode;
        if (resolvedLanguageCode == null || resolvedLanguageCode.isBlank()) {
            store.remove(playerId);
            return false;
        }

        if (!loadedLanguages.containsKey(resolvedLanguageCode)) {
            if (isBlockingSensitiveThread()) {
                warnIfMainThread("loadLanguage");
                scheduleLanguageLoad(resolvedLanguageCode);
            } else if (!loadLanguageBlocking(resolvedLanguageCode)) {
                return false;
            }
        }

        store.put(playerId, resolvedLanguageCode);
        return true;
    }

    private void applyPlayerLocale(PlayerLocale playerLocale) {
        if (playerLocale == null || !playerLocale.isValid()) {
            return;
        }
        setAutoDetectedPlayerLanguage(playerLocale.playerId(), playerLocale.localeTag());
    }

    private Set<String> expandLanguageCandidates(String languageCode) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String trimmed = languageCode != null ? languageCode.trim() : "";
        if (trimmed.isEmpty()) {
            return candidates;
        }
        candidates.add(trimmed);

        String lowerCased = trimmed.toLowerCase(Locale.ROOT);
        candidates.add(lowerCased);

        String normalizedTag = normalizeLanguageKey(trimmed);
        candidates.add(normalizedTag);

        int hyphenIndex = normalizedTag.indexOf('-');
        if (hyphenIndex > 0) {
            candidates.add(normalizedTag.substring(0, hyphenIndex));
        }

        int underscoreIndex = lowerCased.indexOf('_');
        if (underscoreIndex > 0) {
            candidates.add(lowerCased.substring(0, underscoreIndex));
        }

        return candidates;
    }

    private String matchAvailableLanguageCode(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (loadedLanguages.containsKey(candidate)) {
            return candidate;
        }

        String normalizedCandidate = normalizeLanguageKey(candidate);
        for (String availableLanguage : getAvailableLanguages()) {
            if (candidate.equals(availableLanguage)) {
                return availableLanguage;
            }
            if (normalizedCandidate.equals(normalizeLanguageKey(availableLanguage))) {
                return availableLanguage;
            }
        }
        if (!createTranslations(candidate).isEmpty()) {
            return candidate;
        }
        return null;
    }

    private String normalizeLanguageKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private void applyLanguage(String languageCode) {
        this.currentLanguage = languageCode;
        this.currentConfig = loadedLanguages.get(languageCode);
    }

    private void applyFallback(String languageCode) {
        this.fallbackLanguage = languageCode;
        fallbackConfig = loadedLanguages.get(languageCode);
    }

    private boolean isBlockingSensitiveThread() {
        return platform != null && platform.threadContext().isBlockingSensitive();
    }

    private void ensureFallbackLoaded() {
        if (fallbackConfig == null && fallbackLanguage != null) {
            if (isBlockingSensitiveThread()) {
                loadLanguageAsync(fallbackLanguage).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        runOnMain(() -> applyFallback(fallbackLanguage),
                                "apply fallback language " + fallbackLanguage);
                    }
                });
            } else {
                loadFallbackLanguageBlocking(fallbackLanguage);
            }
        }
    }

    private void runOnMain(Runnable task, String action) {
        Tasks.runOnMain(platform, task).whenComplete((ignored, error) -> {
            if (error != null) {
                logger.warn("Failed to run language action on main thread: " + action, error);
            }
        });
    }

    private boolean languageFileExists(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        Path base = platform.configDir().resolve("lang");
        for (String ext : LANGUAGE_EXTENSIONS) {
            Path candidate = base.resolve(languageCode + "." + ext);
            if (candidate.toFile().exists()) {
                return true;
            }
        }
        return false;
    }

    private void warnIfMainThread(String action) {
        if (isBlockingSensitiveThread()) {
            logger.warn("LanguageManager." + action + " performs disk I/O. Consider using "
                    + action + "Async() to avoid main-thread stalls.");
        }
    }

    private String resolveMessage(String languageCode, String key) {
        LanguageConfig primary = getOrLoadLanguage(languageCode);
        if (primary != null) {
            String message = primary.getMessage(key);
            if (message != null) {
                return message;
            }
        }

        String inMemory = resolveInMemoryMessage(languageCode, key);
        if (inMemory != null) {
            return inMemory;
        }

        ensureFallbackLoaded();
        if (fallbackConfig != null) {
            String fallbackMessage = fallbackConfig.getMessage(key);
            if (fallbackMessage != null) {
                return fallbackMessage;
            }
        }

        logMissing(languageCode, key);
        return key;
    }

    private boolean hasMessage(LanguageConfig config, String key) {
        if (config == null) return false;
        return config.getMessage(key) != null;
    }

    private String resolveInMemoryMessage(String languageCode, String key) {
        if (languageCode == null || languageCode.isBlank() || key == null || key.isBlank()) {
            return null;
        }

        Map<String, String> registered = registeredTranslations.get(languageCode);
        if (registered != null) {
            String registeredMessage = registered.get(key);
            if (registeredMessage != null) {
                return registeredMessage;
            }
        }

        Map<String, Map<String, String>> bundled = createTranslations(languageCode);
        if (bundled.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> sectionEntry : bundled.entrySet()) {
            if (sectionEntry == null || sectionEntry.getKey() == null || sectionEntry.getValue() == null) {
                continue;
            }
            String section = sectionEntry.getKey();
            if (!key.startsWith(section + ".")) {
                continue;
            }
            String relativeKey = key.substring(section.length() + 1);
            String bundledMessage = sectionEntry.getValue().get(relativeKey);
            if (bundledMessage != null) {
                return bundledMessage;
            }
        }
        return null;
    }

    private void logMissing(String languageCode, String key) {
        if (!logMissingMessages) {
            return;
        }
        String composite = languageCode + "::" + key;
        if (loggedMissingMessages.add(composite)) {
            String fallbackInfo = fallbackLanguage != null ? ("; fallback=" + fallbackLanguage) : "";
            logger.debug("Missing translation for key '" + key + "' in language '" + languageCode + "'" + fallbackInfo);
        }
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders) {
        return applyPlaceholders(message, placeholders, false);
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders, boolean escapeTags) {
        if (message == null || placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null || !placeholders.containsKey(key)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String value = placeholders.get(key);
            if (value == null) {
                value = "";
            } else if (escapeTags) {
                value = MINI_MESSAGE.escapeTags(value);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String applyReplacements(String message, String... replacements) {
        return applyReplacements(message, false, replacements);
    }

    private String applyReplacements(String message, boolean escapeTags, String... replacements) {
        if (message == null || replacements == null || replacements.length == 0) {
            return message;
        }
        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];
            if (value == null) {
                value = "";
            } else if (escapeTags) {
                value = MINI_MESSAGE.escapeTags(value);
            }
            result = result.replace("{" + placeholder + "}", value);
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private Map<String, String> normalizeTranslations(Map<String, String> translations) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    private Map<String, Map<String, String>> createTranslations(String languageCode) {
        return LanguageDefaults.localizedSections(languageCode);
    }

    private UUID extractUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Audience audience) {
            return audience.id();
        }
        try {
            var m = obj.getClass().getMethod("getUniqueId");
            Object res = m.invoke(obj);
            if (res instanceof UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
        return null;
    }

    private static Platform resolvePlatform(Object platformOrPlugin) {
        if (platformOrPlugin instanceof Platform) {
            return (Platform) platformOrPlugin;
        }
        if (platformOrPlugin != null) {
            try {
                Class<?> providerClass = Class
                        .forName("dev.ua.theroer.magicutils.platform.bukkit.BukkitPlatformProvider");
                for (var ctor : providerClass.getConstructors()) {
                    if (ctor.getParameterCount() == 1
                            && ctor.getParameterTypes()[0].isAssignableFrom(platformOrPlugin.getClass())) {
                        Object instance = ctor.newInstance(platformOrPlugin);
                        if (instance instanceof Platform) {
                            return (Platform) instance;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalArgumentException("Platform provider is required to initialize LanguageManager");
    }
}
