package dev.ua.theroer.magicutils.commands;

/**
 * How to compare values when evaluating permission conditions.
 */
public enum CompareMode {
    /** Try UUID, then name, then equals. */
    AUTO,
    /** Compare by UUID only. */
    UUID,
    /** Compare by name (case-insensitive). */
    NAME,
    /** Use Object.equals directly. */
    EQUALS
}
