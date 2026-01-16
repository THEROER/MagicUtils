package dev.ua.theroer.magicutils.platform;

/**
 * Describes the current execution context for platform thread checks.
 */
public enum ThreadContext {
    /** Main server thread. */
    MAIN,
    /** Platform event-loop thread. */
    EVENT_LOOP,
    /** Network I/O thread. */
    NETWORK,
    /** Generic worker or async thread. */
    WORKER,
    /** Unknown or unclassified thread. */
    UNKNOWN;

    /**
     * Returns true when blocking operations should be avoided.
     *
     * @return true if blocking work is unsafe
     */
    public boolean isBlockingSensitive() {
        return this == MAIN || this == EVENT_LOOP || this == NETWORK || this == UNKNOWN;
    }
}
