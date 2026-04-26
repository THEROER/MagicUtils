package dev.ua.theroer.magicutils.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class BungeeCommandWrapper extends Command implements TabExecutor {
    private final CommandManager<CommandSender> commandManager;
    private final String commandLabel;
    private final Executor asyncExecutor;

    BungeeCommandWrapper(CommandManager<CommandSender> commandManager,
                         String commandLabel,
                         String[] aliases,
                         Executor asyncExecutor) {
        super(commandLabel, null, aliases != null ? aliases : new String[0]);
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.commandLabel = Objects.requireNonNull(commandLabel, "commandLabel");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (sender == null) {
            return;
        }
        List<String> arguments = args != null ? Arrays.asList(args) : List.of();
        CommandThreading threading = commandManager.resolveThreading(commandLabel, arguments);
        if (threading == CommandThreading.ASYNC) {
            CompletableFuture.supplyAsync(
                            () -> runCommand(sender, arguments),
                            asyncExecutor
                    )
                    .thenAccept(result -> sendResult(sender, result));
            return;
        }
        sendResult(sender, runCommand(sender, arguments));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (sender == null) {
            return Collections.emptyList();
        }
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }
        List<String> arguments = args != null ? Arrays.asList(args) : List.of();
        return commandManager.getSuggestions(commandLabel, sender, arguments);
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        String targetSubName = null;
        return commandManager.canAccessCommand(commandLabel, sender, targetSubName);
    }

    private CommandResult runCommand(CommandSender sender, List<String> args) {
        try {
            return commandManager.execute(commandLabel, sender, args);
        } catch (Exception ignored) {
            return CommandResult.failure("Internal command error.");
        }
    }

    private void sendResult(CommandSender sender, CommandResult result) {
        if (sender == null || result == null || !result.isSendMessage()) {
            return;
        }
        String message = result.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        sender.sendMessage(TextComponent.fromLegacyText(message));
    }
}
