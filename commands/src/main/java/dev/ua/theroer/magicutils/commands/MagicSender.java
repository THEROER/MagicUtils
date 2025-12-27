package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-agnostic command sender wrapper.
 */
public interface MagicSender {
    /**
     * Audience for sending messages back to the sender.
     *
     * @return audience wrapper
     */
    Audience audience();

    /**
     * Human-readable sender name.
     *
     * @return sender name
     */
    String name();

    /**
     * Optional sender id (player UUID, if available).
     *
     * @return sender id or null
     */
    default @Nullable UUID id() {
        Audience audience = audience();
        return audience != null ? audience.id() : null;
    }

    /**
     * Permission check for the sender.
     *
     * @param permission permission node
     * @return true if granted
     */
    boolean hasPermission(String permission);

    /**
     * Raw platform sender handle.
     *
     * @return underlying sender object
     */
    @Nullable
    Object handle();

    /**
     * Attempt to unwrap the raw handle to the given type.
     *
     * @param type target class
     * @param <T> type parameter
     * @return unwrapped handle or null if not compatible
     */
    default @Nullable <T> T unwrap(Class<T> type) {
        Object raw = handle();
        if (type == null || raw == null || !type.isInstance(raw)) {
            return null;
        }
        return type.cast(raw);
    }
}
