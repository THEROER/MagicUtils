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

    /**
     * Checks whether this audience has the given permission with a custom op-level fallback.
     *
     * <p>Implementations that can resolve permissions with platform-specific fallback handling
     * should override this method. The default implementation preserves existing behavior.</p>
     *
     * @param permission permission node
     * @param fallbackOpLevel op level to treat as granted when no permission backend answers
     * @return true when allowed
     */
    default boolean hasPermission(String permission, int fallbackOpLevel) {
        return hasPermission(permission);
    }

    /**
     * Optional display name for this audience (e.g., player username).
     *
     * @return audience name or null if not applicable
     */
    default String name() {
        return null;
    }
}
