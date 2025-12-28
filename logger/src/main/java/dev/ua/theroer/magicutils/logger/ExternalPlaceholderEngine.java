package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Hook for integrating external placeholder engines.
 */
@FunctionalInterface
public interface ExternalPlaceholderEngine {
    /** No-op placeholder engine implementation. */
    ExternalPlaceholderEngine NOOP = (audience, text) -> text;

    /**
     * Applies external placeholder processing to plain text.
     *
     * @param audience audience context
     * @param text input text
     * @return processed text
     */
    String apply(Audience audience, String text);

    /**
     * Returns a tag resolver for MiniMessage parsing.
     *
     * @param audience audience context
     * @return tag resolver
     */
    default TagResolver tagResolver(Audience audience) {
        return TagResolver.empty();
    }

    /**
     * Returns a Kyori audience wrapper for the platform audience.
     *
     * @param audience audience context
     * @return adventure audience or null
     */
    default net.kyori.adventure.audience.Audience adventureAudience(Audience audience) {
        return null;
    }

    /**
     * Applies external placeholder processing to a component.
     *
     * @param audience audience context
     * @param component component to process
     * @return processed component
     */
    default Component applyComponent(Audience audience, Component component) {
        return component;
    }
}
