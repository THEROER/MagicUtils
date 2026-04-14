package dev.ua.theroer.magicutils.platform;

/**
 * Normalized player lifecycle transition observed by the platform layer.
 */
public enum PlayerLifecycleType {
    /** Player has joined the server. */
    JOIN,
    /** Player has left the server. */
    LEAVE
}
