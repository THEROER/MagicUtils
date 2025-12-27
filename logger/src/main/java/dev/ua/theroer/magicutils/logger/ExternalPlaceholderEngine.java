package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

@FunctionalInterface
public interface ExternalPlaceholderEngine {
    ExternalPlaceholderEngine NOOP = (audience, text) -> text;

    String apply(Audience audience, String text);

    default TagResolver tagResolver(Audience audience) {
        return TagResolver.empty();
    }

    default net.kyori.adventure.audience.Audience adventureAudience(Audience audience) {
        return null;
    }

    default Component applyComponent(Audience audience, Component component) {
        return component;
    }
}
