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
 */
public class LogBuilderCore {
    protected final LoggerCore logger;
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

    public List<Audience> getRecipients() {
        return new ArrayList<>(recipients);
    }

    public List<TagResolver> getTagResolvers() {
        return new ArrayList<>(tagResolvers);
    }

    public Object[] getArgs() {
        return args != null ? args.clone() : null;
    }

    public Map<String, Object> getPlaceholders() {
        return placeholders != null ? new HashMap<>(placeholders) : null;
    }

    public LogBuilderCore target(LogTarget target) {
        this.target = target;
        return this;
    }

    public LogBuilderCore to(Audience audience) {
        this.audience = audience;
        return this;
    }

    public LogBuilderCore toAudiences(Collection<? extends Audience> audiences) {
        if (audiences != null) {
            this.recipients.addAll(audiences);
        }
        return this;
    }

    public LogBuilderCore recipient(Audience audience) {
        if (audience != null) {
            this.recipients.add(audience);
        }
        return this;
    }

    public LogBuilderCore toAll() {
        this.broadcast = true;
        return this;
    }

    public LogBuilderCore toConsole() {
        this.target = LogTarget.CONSOLE;
        return this;
    }

    public LogBuilderCore noPrefix() {
        this.noPrefix = true;
        this.prefixOverride = PrefixMode.NONE;
        return this;
    }

    public LogBuilderCore prefixMode(PrefixMode mode) {
        this.prefixOverride = mode;
        return this;
    }

    public LogBuilderCore args(Object... args) {
        this.args = args != null ? args.clone() : null;
        return this;
    }

    public LogBuilderCore placeholders(Map<String, Object> placeholders) {
        this.placeholders = placeholders != null ? new HashMap<>(placeholders) : null;
        return this;
    }

    public LogBuilderCore withResolvers(TagResolver... resolvers) {
        if (resolvers != null) {
            for (TagResolver resolver : resolvers) {
                if (resolver != null) {
                    this.tagResolvers.add(resolver);
                }
            }
        }
        return this;
    }

    public void send(Object message, Object... placeholders) {
        if (prefixOverride != null) {
            PrefixMode savedChatMode = null;
            PrefixMode savedConsoleMode = null;

            try {
                savedChatMode = logger.getChatPrefixMode();
                savedConsoleMode = logger.getConsolePrefixMode();

                logger.setChatPrefixMode(prefixOverride);
                logger.setConsolePrefixMode(prefixOverride);

                performSend(message, placeholders);
            } finally {
                if (savedChatMode != null) {
                    logger.setChatPrefixMode(savedChatMode);
                }
                if (savedConsoleMode != null) {
                    logger.setConsolePrefixMode(savedConsoleMode);
                }
            }
        } else {
            performSend(message, placeholders);
        }
    }

    protected void performSend(Object message, Object... placeholders) {
        LogTarget finalTarget = target != null ? target : logger.getDefaultTarget();

        Collection<? extends Audience> audienceRecipients = null;
        if (!recipients.isEmpty()) {
            audienceRecipients = new ArrayList<>(recipients);
        }

        logger.send(level, message, audience, audienceRecipients, finalTarget, broadcast, placeholders);
    }
}
