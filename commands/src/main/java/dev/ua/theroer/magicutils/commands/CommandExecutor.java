package dev.ua.theroer.magicutils.commands;

/**
 * Executes a command action built by the command builder API.
 *
 * @param <S> sender type
 */
@FunctionalInterface
public interface CommandExecutor<S> {
    /**
     * Executes the command with the given execution context.
     *
     * @param execution execution context
     * @return command result
     */
    CommandResult execute(CommandExecution<S> execution);
}
