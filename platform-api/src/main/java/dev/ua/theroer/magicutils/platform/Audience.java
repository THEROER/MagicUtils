package dev.ua.theroer.magicutils.platform;

import net.kyori.adventure.text.Component;
import java.util.UUID;

/**
 * Minimal recipient abstraction for platform-agnostic messaging.
 */
public interface Audience {
    /**
     * Sends a formatted component to the audience.
     *
     * @param component message to deliver
     */
    void send(Component component);

    /**
     * Optional unique id for audience (e.g., player UUID).
     *
     * @return audience identifier or null if not applicable
     */
    default UUID id() {
        return null;
    }

    /**
     * Checks whether this audience has the given permission.
     *
     * <p>Implementations that can resolve permissions should override this method.
     * Default behavior only allows empty permission checks.</p>
     *
     * @param permission permission node
     * @return true when allowed
     */
    default boolean hasPermission(String permission) {
        return permission == null || permission.isBlank();
    }
}
