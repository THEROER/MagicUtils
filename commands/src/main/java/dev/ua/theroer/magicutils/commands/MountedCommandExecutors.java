package dev.ua.theroer.magicutils.commands;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Adapts the handlers of a mounted {@link MagicCommand} into {@code CommandExecutor<Object>}:
 * builder executors are re-bound to the mounted command instance, and {@code @SubCommand}
 * methods are invoked reflectively with their results normalized into a {@link CommandResult}.
 */
final class MountedCommandExecutors {

    private MountedCommandExecutors() {
    }

    @SuppressWarnings("unchecked")
    static CommandExecutor<Object> wrap(MagicCommand command, CommandExecutor<?> executor) {
        if (executor == null) {
            return null;
        }
        CommandExecutor<Object> typed = (CommandExecutor<Object>) executor;
        return execution -> typed.execute(new CommandExecution<>(
                command,
                execution.commandName(),
                execution.subCommandName(),
                execution.sender(),
                execution.rawArgs(),
                execution.arguments(),
                execution.parsedArgs()
        ));
    }

    static CommandExecutor<Object> forMethod(MagicCommand command, Method method) {
        return execution -> {
            try {
                Object result = method.invoke(command, execution.parsedArgs());
                return resolveResult(result);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Failed to invoke mounted command method", cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to invoke mounted command method", e);
            }
        };
    }

    /**
     * Normalizes a {@code @SubCommand} method's return value into a
     * {@link CommandResult}. A method may return a {@code CommandResult} directly, a
     * {@link CompletableFuture} of one (awaited here — safe because {@code ASYNC}
     * handlers already run off the main thread, letting the handler stay free of
     * manual {@code join()}/callback boilerplate), or {@code void}/{@code null}
     * (treated as success).
     */
    static CommandResult resolveResult(Object result) {
        if (result instanceof CommandResult commandResult) {
            return commandResult;
        }
        if (result instanceof CompletableFuture<?> future) {
            try {
                Object awaited = future.join();
                return awaited instanceof CommandResult commandResult
                        ? commandResult
                        : CommandResult.success();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Async command handler failed", cause);
            }
        }
        return CommandResult.success();
    }
}
