package dev.ua.theroer.magicutils.commands;

/**
 * Threading policy for command execution.
 */
public enum CommandThreading {
    /**
     * Always execute on the calling thread (usually main).
     */
    MAIN,
    /**
     * Execute on a worker thread.
     */
    ASYNC
}
