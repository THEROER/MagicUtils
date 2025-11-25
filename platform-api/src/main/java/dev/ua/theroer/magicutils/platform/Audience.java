package dev.ua.theroer.magicutils.platform;

import net.kyori.adventure.text.Component;
import java.util.UUID;

/**
 * Minimal recipient abstraction for platform-agnostic messaging.
 */
public interface Audience {
    /**
     * Sends a formatted component to the audience.
     */
    void send(Component component);

    /**
     * Optional unique id for audience (e.g., player UUID).
     */
    default UUID id() {
        return null;
    }
}
