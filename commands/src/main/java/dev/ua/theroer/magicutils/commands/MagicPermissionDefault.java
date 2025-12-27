package dev.ua.theroer.magicutils.commands;

/**
 * Mirrors Bukkit PermissionDefault for platform-agnostic code.
 */
public enum MagicPermissionDefault {
    /** Never granted. */
    FALSE,
    /** Granted to non-ops. */
    NOT_OP,
    /** Granted to ops. */
    OP,
    /** Granted to everyone. */
    TRUE
}
