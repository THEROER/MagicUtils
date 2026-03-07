package dev.ua.theroer.magicutils.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class VelocityCommandWrapper implements SimpleCommand {
    private final CommandManager<CommandSource> commandManager;
    private final String commandLabel;
    private final Executor asyncExecutor;

    VelocityCommandWrapper(CommandManager<CommandSource> commandManager,
                           String commandLabel,
                           Executor asyncExecutor) {
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.commandLabel = Objects.requireNonNull(commandLabel, "commandLabel");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation == null || invocation.source() == null) {
            return;
        }
        List<String> args = Arrays.asList(invocation.arguments());
        String label = resolveExecutionLabel(invocation.alias());
        CommandThreading threading = commandManager.resolveThreading(label, args);
        if (threading == CommandThreading.ASYNC) {
            CompletableFuture.supplyAsync(
                            () -> runCommand(label, invocation.source(), args),
                            asyncExecutor
                    )
                    .thenAccept(result -> sendResult(invocation.source(), result));
            return;
        }
        sendResult(invocation.source(), runCommand(label, invocation.source(), args));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation == null || invocation.source() == null) {
            return Collections.emptyList();
        }
        if (!hasPermission(invocation)) {
            return Collections.emptyList();
        }
        String label = resolveExecutionLabel(invocation.alias());
        return commandManager.getSuggestions(label, invocation.source(), Arrays.asList(invocation.arguments()));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (invocation == null || invocation.source() == null) {
            return false;
        }
        String label = resolveExecutionLabel(invocation.alias());
        String targetSubName = invocation.arguments().length > 0 ? invocation.arguments()[0] : null;
        return commandManager.canAccessCommand(label, invocation.source(), targetSubName);
    }

    private String resolveExecutionLabel(String invocationAlias) {
        if (invocationAlias == null || invocationAlias.isBlank()) {
            return commandLabel;
        }
        return invocationAlias.toLowerCase(Locale.ROOT);
    }

    private CommandResult runCommand(String label, CommandSource source, List<String> args) {
        try {
            return commandManager.execute(label, source, args);
        } catch (Exception ignored) {
            return CommandResult.failure("Internal command error.");
        }
    }

    private void sendResult(CommandSource source, CommandResult result) {
        if (source == null || result == null || !result.isSendMessage()) {
            return;
        }
        String message = result.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        source.sendMessage(Component.text(message));
    }
}
