package dev.ua.theroer.magicutils.commands;

/**
 * Declarative condition types for argument/command permissions.
 */
public enum PermissionConditionType {
    /** Always check permission. */
    ALWAYS,
    /** Require non-null target(s). */
    NOT_NULL,
    /** Target matches sender. */
    SELF,
    /** Target differs from sender. */
    OTHER,
    /** Any target differs from sender. */
    ANY_OTHER,
    /** Any two targets differ. */
    DISTINCT,
    /** All targets differ. */
    ALL_DISTINCT,
    /** Targets are equal to each other. */
    EQUALS,
    /** Targets are not equal to each other. */
    NOT_EQUALS,
    /** At least one target exists (non-null). */
    EXISTS
}
