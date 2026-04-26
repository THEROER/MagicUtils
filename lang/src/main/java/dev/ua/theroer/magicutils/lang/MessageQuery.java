package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.AudienceResolver;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Fluent builder for cases that don't fit the direct
 * {@link Messages#getRaw}, {@link Messages#get}, or
 * {@link Messages#send} methods — primarily explicit language pinning
 * and manual escape-mode control.
 *
 * <pre>{@code
 * Messages.query("plugin.welcome")
 *         .audience(player)
 *         .args("name", playerName)
 *         .send();
 *
 * Component c = Messages.query("plugin.error")
 *         .language("en")          // force English regardless of audience
 *         .escaped()
 *         .args(Map.of("error", e.getMessage()))
 *         .component();
 * }</pre>
 */
public final class MessageQuery {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Function<String, String> ESCAPE_TAGS = MINI_MESSAGE::escapeTags;

    private final LanguageManager manager;
    private final String key;
    private Audience audience;
    private String pinnedLanguage;
    private Object[] args;
    private boolean escapeTags;

    MessageQuery(LanguageManager manager, String key) {
        this.manager = manager;
        this.key = key;
    }

    /**
     * Resolves the message in the recipient's preferred language.
     * Accepts any sender-like object — Audience, Player, etc.
     *
     * @param recipient Audience, platform sender, or {@code null}
     * @return this builder
     */
    public MessageQuery audience(@Nullable Object recipient) {
        this.audience = AudienceResolver.resolve(recipient);
        return this;
    }

    /**
     * Forces resolution into the given language code, ignoring any
     * audience preference.
     *
     * @param languageCode language code (e.g. {@code "en"})
     * @return this builder
     */
    public MessageQuery language(String languageCode) {
        this.pinnedLanguage = languageCode;
        return this;
    }

    /**
     * Sets the placeholder values. Accepts the same shapes as
     * {@link MsgFmt#apply}: a {@code Map}, an {@code Iterable}, an
     * array, key-value pairs, or positional values.
     *
     * @param args placeholder values
     * @return this builder
     */
    public MessageQuery args(Object... args) {
        this.args = args;
        return this;
    }

    /**
     * Escapes placeholder values before substitution to neutralize any
     * MiniMessage tags they might contain. Always on for
     * {@link #component()} and {@link #send}, optional for
     * {@link #raw()}.
     *
     * @return this builder
     */
    public MessageQuery escaped() {
        this.escapeTags = true;
        return this;
    }

    /**
     * Resolves to a plain string.
     *
     * @return resolved string or the key itself
     */
    public String raw() {
        if (manager == null) {
            return key;
        }
        String resolved = resolveBase();
        if (args == null || args.length == 0) {
            return resolved;
        }
        return MsgFmt.apply(resolved, escapeTags ? ESCAPE_TAGS : null, args);
    }

    /**
     * Resolves to a {@link Component}, parsing through MiniMessage.
     * Placeholder values are always tag-escaped.
     *
     * @return adventure component
     */
    public Component component() {
        if (manager == null) {
            return MINI_MESSAGE.deserialize(key);
        }
        String resolved = resolveBase();
        String formatted = (args == null || args.length == 0)
                ? resolved
                : MsgFmt.apply(resolved, ESCAPE_TAGS, args);
        return MINI_MESSAGE.deserialize(formatted);
    }

    /**
     * Sends the resolved component to the audience set on the builder.
     *
     * @throws IllegalStateException when no audience has been set
     */
    public void send() {
        if (audience == null) {
            throw new IllegalStateException(
                    "MessageQuery.send() requires audience(...) or send(audience)");
        }
        audience.send(component());
    }

    /**
     * Sends the resolved component to the given recipient, overriding
     * any previously-set audience.
     *
     * @param recipient Audience, platform sender, or {@code null} (no-op)
     */
    public void send(@Nullable Object recipient) {
        Audience target = AudienceResolver.resolve(recipient);
        if (target == null) {
            return;
        }
        this.audience = target;
        target.send(component());
    }

    private String resolveBase() {
        if (pinnedLanguage != null && !pinnedLanguage.isBlank()) {
            return manager.getMessageIn(pinnedLanguage, key);
        }
        if (audience != null) {
            return manager.getMessageFor(audience, key);
        }
        return manager.getMessage(key);
    }
}
