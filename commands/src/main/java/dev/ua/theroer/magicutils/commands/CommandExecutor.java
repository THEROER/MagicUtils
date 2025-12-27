package dev.ua.theroer.magicutils.commands;

/**
 * Executes a command action built by the command builder API.
 *
 * @param <S> sender type
 */
@FunctionalInterface
public interface CommandExecutor<S> {
    CommandResult execute(CommandExecution<S> execution);
}
