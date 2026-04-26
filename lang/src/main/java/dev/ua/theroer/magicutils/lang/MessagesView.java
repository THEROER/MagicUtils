package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.AudienceResolver;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Scope-aware mirror of {@link Messages}. Resolves the backing
 * {@link LanguageManager} lazily per call so re-registration of the
 * scope while a view is held remains valid.
 */
public final class MessagesView {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Function<String, String> ESCAPE_TAGS = MINI_MESSAGE::escapeTags;

    private final String scope;
    private final LanguageManager manager;

    MessagesView(String scope) {
        this.scope = scope;
        this.manager = null;
    }

    MessagesView(LanguageManager manager) {
        this.scope = null;
        this.manager = manager;
    }

    /**
     * @return scope identifier or {@code null} when bound directly
     */
    public String scope() {
        return scope;
    }

    /**
     * @return resolved language manager or {@code null}
     */
    public LanguageManager getLanguageManager() {
        return resolveManager();
    }

    // ─── Direct resolution: raw string ─────────────────────────────────

    public String getRaw(String key) {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getMessage(key) : key;
    }

    public String getRaw(@Nullable Object recipient, String key) {
        LanguageManager resolved = resolveManager();
        if (resolved == null) {
            return key;
        }
        Audience audience = AudienceResolver.resolve(recipient);
        return audience != null ? resolved.getMessageFor(audience, key) : resolved.getMessage(key);
    }

    public String getRaw(@Nullable Object recipient, String key, Object... args) {
        return MsgFmt.apply(getRaw(recipient, key), args);
    }

    // ─── Direct resolution: component ──────────────────────────────────

    public Component get(String key) {
        return MINI_MESSAGE.deserialize(getRaw(key));
    }

    public Component get(@Nullable Object recipient, String key) {
        return MINI_MESSAGE.deserialize(getRaw(recipient, key));
    }

    public Component get(@Nullable Object recipient, String key, Object... args) {
        return MINI_MESSAGE.deserialize(MsgFmt.apply(getRaw(recipient, key), ESCAPE_TAGS, args));
    }

    // ─── Send ──────────────────────────────────────────────────────────

    public void send(@Nullable Object recipient, String key) {
        Audience audience = AudienceResolver.resolve(recipient);
        if (audience == null) {
            return;
        }
        audience.send(get(audience, key));
    }

    public void send(@Nullable Object recipient, String key, Object... args) {
        Audience audience = AudienceResolver.resolve(recipient);
        if (audience == null) {
            return;
        }
        audience.send(get(audience, key, args));
    }

    // ─── Builder + meta ────────────────────────────────────────────────

    public MessageQuery query(String key) {
        return new MessageQuery(resolveManager(), key);
    }

    public boolean exists(String key) {
        LanguageManager resolved = resolveManager();
        return resolved != null && resolved.hasMessage(key);
    }

    public String currentLanguage() {
        LanguageManager resolved = resolveManager();
        return resolved != null ? resolved.getCurrentLanguage() : "en";
    }

    public String getCurrentLanguage() {
        return currentLanguage();
    }

    private LanguageManager resolveManager() {
        if (manager != null) {
            return manager;
        }
        if (scope != null) {
            return Messages.getLanguageManager(scope);
        }
        return Messages.getLanguageManager();
    }
}
