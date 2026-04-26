package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.AudienceResolver;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Global entry point for resolving and sending localized messages.
 *
 * <p>Three flavours of API:</p>
 * <ul>
 *     <li><b>{@link #getRaw}, {@link #get}, {@link #send}</b> — direct
 *         methods covering 95% of call sites. Accept any sender-like
 *         object (Audience / Player / null) and any placeholder shape
 *         (Map / varargs key-value pairs / positional args).</li>
 *     <li><b>{@link #query(String)}</b> — fluent builder for the rare
 *         cases that need explicit language pinning, manual escape
 *         control, or are otherwise too complex for one call.</li>
 *     <li><b>{@link #view(String)}</b> — same surface but resolved
 *         against a named per-plugin scope.</li>
 * </ul>
 *
 * <p>Rendering rules:</p>
 * <ul>
 *     <li>{@link #getRaw} returns a plain string. Placeholders are
 *         substituted but their values are <em>not</em> tag-escaped, so
 *         a translation containing {@code <red>{name}</red>} will
 *         interpret {@code {name}} as MiniMessage text on later parsing.
 *         Use this when callers control the placeholder values.</li>
 *     <li>{@link #get} produces a {@link Component} via MiniMessage and
 *         <em>always</em> escapes placeholder values, so untrusted user
 *         input cannot inject tags.</li>
 *     <li>{@link #send} resolves like {@link #get} and dispatches the
 *         component to the audience, in the audience's preferred
 *         language.</li>
 * </ul>
 *
 * <p>Plugin-specific overrides:</p>
 * <ul>
 *     <li>{@link #isOverride(String, String)} — true when the user has
 *         configured a non-default value.</li>
 *     <li>{@link #resolveOverride(Object, String, String, Object...)} —
 *         pick the override if one exists, else fall back to the
 *         localized translation.</li>
 * </ul>
 */
public final class Messages {
    private static final String DEFAULT_SCOPE = "default";
    private static final Map<String, LanguageManager> LANGUAGE_MANAGERS = new ConcurrentHashMap<>();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Function<String, String> ESCAPE_TAGS = MINI_MESSAGE::escapeTags;
    private static volatile LanguageManager languageManager;

    private Messages() {
    }

    // ─── Registration / scopes ─────────────────────────────────────────

    /**
     * Sets the default (global) language manager.
     *
     * @param manager language manager or {@code null} to clear
     */
    public static void setLanguageManager(LanguageManager manager) {
        languageManager = manager;
        if (manager != null) {
            LANGUAGE_MANAGERS.put(DEFAULT_SCOPE, manager);
        } else {
            LANGUAGE_MANAGERS.remove(DEFAULT_SCOPE);
        }
    }

    /**
     * @return the default language manager, or {@code null}
     */
    public static LanguageManager getLanguageManager() {
        return languageManager;
    }

    /**
     * Registers a scoped (per-plugin) language manager.
     *
     * @param scope scope identifier (typically plugin/mod name)
     * @param manager language manager, or {@code null} to unregister
     */
    public static void register(String scope, LanguageManager manager) {
        String key = normalizeScope(scope);
        if (manager == null) {
            LANGUAGE_MANAGERS.remove(key);
            if (DEFAULT_SCOPE.equals(key)) {
                languageManager = null;
            }
            return;
        }
        LANGUAGE_MANAGERS.put(key, manager);
        if (DEFAULT_SCOPE.equals(key)) {
            languageManager = manager;
        }
    }

    /**
     * Unregisters a scoped language manager.
     *
     * @param scope scope identifier
     */
    public static void unregister(String scope) {
        register(scope, null);
    }

    /**
     * @param scope scope identifier
     * @return scoped language manager or {@code null}
     */
    public static LanguageManager getLanguageManager(String scope) {
        return LANGUAGE_MANAGERS.get(normalizeScope(scope));
    }

    /**
     * @param scope scope identifier
     * @return scoped view for message resolution
     */
    public static MessagesView view(String scope) {
        return new MessagesView(normalizeScope(scope));
    }

    /**
     * @param manager language manager to bind the view to
     * @return view bound directly to the manager
     */
    public static MessagesView view(LanguageManager manager) {
        return new MessagesView(manager);
    }

    // ─── Direct resolution: raw string ─────────────────────────────────

    /**
     * Resolves a key against the default language manager.
     *
     * @param key message key
     * @return resolved string, or the key itself when no manager is set
     */
    public static String getRaw(String key) {
        LanguageManager manager = languageManager;
        return manager != null ? manager.getMessage(key) : key;
    }

    /**
     * Resolves a key in the recipient's preferred language.
     *
     * @param recipient Audience, platform sender, or {@code null}
     * @param key message key
     * @return resolved string
     */
    public static String getRaw(@Nullable Object recipient, String key) {
        LanguageManager manager = languageManager;
        if (manager == null) {
            return key;
        }
        Audience audience = AudienceResolver.resolve(recipient);
        return audience != null ? manager.getMessageFor(audience, key) : manager.getMessage(key);
    }

    /**
     * Resolves a key with placeholder substitution.
     *
     * <p>{@code args} accepts any shape supported by {@link MsgFmt#apply}:
     * a single {@code Map}, an {@code Iterable}, an array, key-value
     * pairs ({@code "name", value, "count", 5}), or positional values.</p>
     *
     * @param recipient Audience, platform sender, or {@code null}
     * @param key message key
     * @param args placeholder values
     * @return resolved string with placeholders applied
     */
    public static String getRaw(@Nullable Object recipient, String key, Object... args) {
        String resolved = getRaw(recipient, key);
        return MsgFmt.apply(resolved, args);
    }

    // ─── Direct resolution: Adventure component ────────────────────────

    /**
     * Resolves a key and parses the result as MiniMessage.
     *
     * @param key message key
     * @return adventure component
     */
    public static Component get(String key) {
        return MINI_MESSAGE.deserialize(getRaw(key));
    }

    /**
     * Resolves a key in the recipient's preferred language and parses as
     * MiniMessage.
     *
     * @param recipient Audience, platform sender, or {@code null}
     * @param key message key
     * @return adventure component
     */
    public static Component get(@Nullable Object recipient, String key) {
        return MINI_MESSAGE.deserialize(getRaw(recipient, key));
    }

    /**
     * Resolves a key with placeholders (values escaped before parsing) and
     * returns an adventure component.
     *
     * @param recipient Audience, platform sender, or {@code null}
     * @param key message key
     * @param args placeholder values
     * @return adventure component
     */
    public static Component get(@Nullable Object recipient, String key, Object... args) {
        String resolved = getRaw(recipient, key);
        return MINI_MESSAGE.deserialize(MsgFmt.apply(resolved, ESCAPE_TAGS, args));
    }

    // ─── Direct send ──────────────────────────────────────────────────

    /**
     * Resolves a key in the recipient's language and sends it as an
     * Adventure component.
     *
     * @param recipient Audience, platform sender, or {@code null} (no-op)
     * @param key message key
     */
    public static void send(@Nullable Object recipient, String key) {
        Audience audience = AudienceResolver.resolve(recipient);
        if (audience == null) {
            return;
        }
        audience.send(get(audience, key));
    }

    /**
     * Resolves a key with placeholders (values escaped) and sends the
     * component to the recipient.
     *
     * @param recipient Audience, platform sender, or {@code null} (no-op)
     * @param key message key
     * @param args placeholder values
     */
    public static void send(@Nullable Object recipient, String key, Object... args) {
        Audience audience = AudienceResolver.resolve(recipient);
        if (audience == null) {
            return;
        }
        audience.send(get(audience, key, args));
    }

    // ─── Override resolution helpers ───────────────────────────────────

    /**
     * Returns true when {@code configured} represents a real override —
     * i.e. it is non-blank and differs from the bundled English default
     * for {@code key}.
     *
     * <p>Useful for plugins where users may keep an English default in
     * config without realising it: comparing against the bundled value
     * lets you tell "user kept the default" from "user wants this exact
     * English text even on Ukrainian clients".</p>
     *
     * @param configured user-configured value (may be null/blank)
     * @param key message key the configured value parallels
     * @return true if {@code configured} should be treated as an override
     */
    public static boolean isOverride(@Nullable String configured, String key) {
        if (configured == null || configured.isBlank()) {
            return false;
        }
        String englishDefault = BundledTranslations.getTranslations("en").get(key);
        if (englishDefault == null) {
            return true;
        }
        return !configured.trim().equals(englishDefault);
    }

    /**
     * Returns {@code configured} (with placeholders applied) when it is
     * an override; otherwise falls back to localizing {@code key} for the
     * recipient.
     *
     * @param recipient Audience, platform sender, or {@code null}
     * @param configured optional user-configured override
     * @param key message key
     * @param args placeholder values
     * @return resolved string
     */
    public static String resolveOverride(@Nullable Object recipient,
                                          @Nullable String configured,
                                          String key,
                                          Object... args) {
        if (isOverride(configured, key)) {
            return MsgFmt.apply(configured, args);
        }
        return getRaw(recipient, key, args);
    }

    // ─── Fluent builder ────────────────────────────────────────────────

    /**
     * Starts a fluent message resolution against the default manager.
     *
     * @param key message key
     * @return builder
     */
    public static MessageQuery query(String key) {
        return new MessageQuery(languageManager, key);
    }

    // ─── Meta ──────────────────────────────────────────────────────────

    /**
     * @param key message key
     * @return true if the default manager has an entry for this key
     */
    public static boolean exists(String key) {
        LanguageManager manager = languageManager;
        return manager != null && manager.hasMessage(key);
    }

    /**
     * @return active language code or {@code "en"} when no manager is set
     */
    public static String currentLanguage() {
        LanguageManager manager = languageManager;
        return manager != null ? manager.getCurrentLanguage() : "en";
    }

    /**
     * @return active language code (legacy alias for {@link #currentLanguage}).
     */
    public static String getCurrentLanguage() {
        return currentLanguage();
    }

    private static String normalizeScope(String scope) {
        String normalized = scope != null ? scope.trim().toLowerCase(Locale.ROOT) : "";
        return normalized.isEmpty() ? DEFAULT_SCOPE : normalized;
    }
}
