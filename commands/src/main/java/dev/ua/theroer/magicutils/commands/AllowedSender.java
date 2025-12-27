package dev.ua.theroer.magicutils.commands;

/**
 * Allowed sender kinds for @Sender annotation.
 */
public enum AllowedSender {
    /** Any sender. */
    ANY,
    /** Player sender. */
    PLAYER,
    /** Console sender. */
    CONSOLE,
    /** Command block sender. */
    BLOCK,
    /** Command minecart sender. */
    MINECART,
    /** Proxied sender (Bungee/Velocity). */
    PROXIED,
    /** Remote console sender. */
    REMOTE
}
