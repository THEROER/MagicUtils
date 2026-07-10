package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import lombok.Getter;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent API builder for logging messages with advanced options.
 *
 * <p>Uses a self-referential type parameter so the shared fluent methods return
 * the concrete platform builder type. Platform subclasses declare
 * {@code class LogBuilder extends LogBuilderCore<LogBuilder>} and only add their
 * player/command-source overloads; they no longer need to re-declare covariant
 * overrides of the shared methods just to keep the chain typed.
 *
 * @param <SELF> concrete builder type returned by the fluent methods
 */
public class LogBuilderCore<SELF extends LogBuilderCore<SELF>> {
    /** Logger core used to dispatch messages. */
    protected final LoggerCore logger;
    /** Log level associated with this builder. */
    protected final LogLevel level;
    @Getter
    private LogTarget target;
    @Getter
    private boolean broadcast = false;
    @Getter
    private Audience audience;
    private final List<Audience> recipients;
    @Getter
    private PrefixMode prefixOverride;
    @Getter
    private boolean noPrefix = false;
    private final List<TagResolver> tagResolvers;
    private Object[] args;
    private Map<String, Object> placeholders;

    /**
     * Creates a new LogBuilderCore with the specified log level.
     *
     * @param logger logger instance to dispatch through
     * @param level the log level for messages built by this instance
     */
    public LogBuilderCore(LoggerCore logger, LogLevel level) {
        this.logger = logger;
        this.level = level;
        this.recipients = new ArrayList<>();
        this.tagResolvers = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private SELF self() {
        return (SELF) this;
    }

    /**
     * Returns a snapshot of queued recipients.
     *
     * @return list of recipients
     */
    public List<Audience> getRecipients() {
        return new ArrayList<>(recipients);
    }

    /**
     * Returns a snapshot of registered tag resolvers.
     *
     * @return list of tag resolvers
     */
    public List<TagResolver> getTagResolvers() {
        return new ArrayList<>(tagResolvers);
    }

    /**
     * Returns the argument array for placeholders.
     *
     * @return argument array or null
     */
    public Object[] getArgs() {
        return args != null ? args.clone() : null;
    }

    /**
     * Returns placeholder mappings.
     *
     * @return placeholder map or null
     */
    public Map<String, Object> getPlaceholders() {
        return placeholders != null ? new HashMap<>(placeholders) : null;
    }

    /**
     * Sets the log target for this message.
     *
     * @param target log target
     * @return this builder
     */
    public SELF target(LogTarget target) {
        this.target = target;
        return self();
    }

    /**
     * Sets the direct audience for the message.
     *
     * @param audience target audience
     * @return this builder
     */
    public SELF to(Audience audience) {
        this.audience = audience;
        return self();
    }

    /**
     * Adds a collection of audiences as recipients.
     *
     * @param audiences recipient collection
     * @return this builder
     */
    public SELF toAudiences(Collection<? extends Audience> audiences) {
        if (audiences != null) {
            this.recipients.addAll(audiences);
        }
        return self();
    }

    /**
     * Adds a single recipient.
     *
     * @param audience recipient
     * @return this builder
     */
    public SELF recipient(Audience audience) {
        if (audience != null) {
            this.recipients.add(audience);
        }
        return self();
    }

    /**
     * Broadcasts the message to all recipients.
     *
     * @return this builder
     */
    public SELF toAll() {
        this.broadcast = true;
        return self();
    }

    /**
     * Forces the message to console target.
     *
     * @return this builder
     */
    public SELF toConsole() {
        this.target = LogTarget.CONSOLE;
        return self();
    }

    /**
     * Disables prefix rendering for this message.
     *
     * @return this builder
     */
    public SELF noPrefix() {
        this.noPrefix = true;
        this.prefixOverride = PrefixMode.NONE;
        return self();
    }

    /**
     * Overrides prefix rendering mode for this message.
     *
     * @param mode prefix mode override
     * @return this builder
     */
    public SELF prefixMode(PrefixMode mode) {
        this.prefixOverride = mode;
        return self();
    }

    /**
     * Sets placeholder arguments.
     *
     * @param args placeholder arguments
     * @return this builder
     */
    public SELF args(Object... args) {
        this.args = args != null ? args.clone() : null;
        return self();
    }

    /**
     * Sets named placeholders.
     *
     * @param placeholders placeholder map
     * @return this builder
     */
    public SELF placeholders(Map<String, Object> placeholders) {
        this.placeholders = placeholders != null ? new HashMap<>(placeholders) : null;
        return self();
    }

    /**
     * Adds MiniMessage tag resolvers.
     *
     * @param resolvers tag resolvers
     * @return this builder
     */
    public SELF withResolvers(TagResolver... resolvers) {
        if (resolvers != null) {
            for (TagResolver resolver : resolvers) {
                if (resolver != null) {
                    this.tagResolvers.add(resolver);
                }
            }
        }
        return self();
    }

    /**
     * Sends the message using the configured builder settings.
     *
     * @param message message to send
     * @param placeholders placeholder arguments
     */
    public void send(Object message, Object... placeholders) {
        performSend(message, placeholders);
    }

    /**
     * Performs the message dispatch with the current builder state.
     *
     * @param message message to send
     * @param placeholders placeholder arguments
     */
    protected void performSend(Object message, Object... placeholders) {
        LogTarget finalTarget = target != null ? target : logger.getDefaultTarget();

        Collection<? extends Audience> audienceRecipients = null;
        if (!recipients.isEmpty()) {
            audienceRecipients = new ArrayList<>(recipients);
        }

        logger.send(level, message, audience, audienceRecipients, finalTarget, broadcast,
                null, null, prefixOverride, placeholders);
    }
}
