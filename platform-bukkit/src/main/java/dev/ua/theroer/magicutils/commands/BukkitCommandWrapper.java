package dev.ua.theroer.magicutils.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Bukkit command wrapper for integrating custom command logic with Bukkit's
 * command system.
 */
public class BukkitCommandWrapper extends Command {
    private final PrefixedLogger commandLogger;
    private final dev.ua.theroer.magicutils.Logger messageLogger;
    private final CommandManager<CommandSender> commandManager;
    private final JavaPlugin plugin;
    private final TaskScheduler scheduler;

    @Getter
    @Setter
    private String detailedUsage;

    /**
     * Constructs a new BukkitCommandWrapper.
     *
     * @param name           the command name
     * @param commandManager the command manager
     * @param commandLogger  command logger
     * @param messageLogger  message logger
     */
    private BukkitCommandWrapper(String name,
                                 CommandManager<CommandSender> commandManager,
                                 PrefixedLogger commandLogger,
                                 dev.ua.theroer.magicutils.Logger messageLogger,
                                 JavaPlugin plugin,
                                 TaskScheduler scheduler) {
        super(name);
        this.commandManager = commandManager;
        this.commandLogger = commandLogger;
        this.messageLogger = messageLogger;
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /**
     * Factory to avoid leaking 'this' before full initialisation.
     *
     * @param name           command name
     * @param commandManager manager to delegate execution
     * @param aliases        optional aliases
     * @param commandLogger  command logger
     * @param messageLogger  message logger
     * @param plugin         owning plugin
     * @param scheduler      task scheduler (nullable)
     * @return fully initialised wrapper
     */
    public static BukkitCommandWrapper create(String name,
                                              CommandManager<CommandSender> commandManager,
                                              List<String> aliases,
                                              PrefixedLogger commandLogger,
                                              dev.ua.theroer.magicutils.Logger messageLogger,
                                              JavaPlugin plugin,
                                              TaskScheduler scheduler) {
        BukkitCommandWrapper wrapper = new BukkitCommandWrapper(name, commandManager, commandLogger, messageLogger,
                plugin, scheduler);
        if (aliases != null && !aliases.isEmpty()) {
            wrapper.setAliases(aliases);
        }
        return wrapper;
    }

    /**
     * Executes the command.
     * 
     * @param sender       the command sender
     * @param commandLabel the command label
     * @param args         the command arguments
     * @return true if the command was successful
     */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        try {
            commandLogger.debug("Executing command: " + commandLabel + " with args: "
                    + Arrays.toString(args) + " by " + sender.getName());

            List<String> argList = Arrays.asList(args);
            CommandThreading threading = commandManager.resolveThreading(commandLabel, argList);
            if (threading == CommandThreading.ASYNC) {
                dispatchAsync(sender, commandLabel, argList);
                return true;
            }

            CommandResult result = commandManager.execute(commandLabel, sender, argList);
            sendResult(sender, result);
            // Returning false makes Bukkit emit plugin usage text, even when the
            // command already handled the failure and chose its own response.
            return true;
        } catch (Exception e) {
            messageLogger.error("Error executing command " + commandLabel + ": " + e.getMessage());
            if (sender instanceof Player) {
                messageLogger.error().to((Player) sender).send(InternalMessages.CMD_INTERNAL_ERROR.get());
            } else {
                messageLogger.error(InternalMessages.CMD_INTERNAL_ERROR.get());
            }
            e.printStackTrace();
            return false;
        }
    }

    private void dispatchAsync(CommandSender sender, String commandLabel, List<String> args) {
        if (scheduler == null) {
            CommandResult result = commandManager.execute(commandLabel, sender, args);
            sendResult(sender, result);
            return;
        }
        scheduler.cpu().execute(() -> {
            CommandResult result;
            try {
                result = commandManager.execute(commandLabel, sender, args);
            } catch (Exception e) {
                messageLogger.error("Error executing command " + commandLabel + ": " + e.getMessage());
                result = CommandResult.failure(InternalMessages.CMD_INTERNAL_ERROR.get());
            }
            CommandResult finalResult = result;
            runOnMain(() -> sendResult(sender, finalResult));
        });
    }

    private void runOnMain(Runnable task) {
        if (task == null) {
            return;
        }
        if (plugin == null) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private void sendResult(CommandSender sender, CommandResult result) {
        if (result == null || !result.isSendMessage()) {
            return;
        }
        String message = result.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        if (result.isSuccess()) {
            if (sender instanceof Player) {
                messageLogger.success().target(LogTarget.CHAT).to((Player) sender).send(message);
            } else {
                messageLogger.success(message);
            }
        } else {
            if (sender instanceof Player) {
                messageLogger.error().target(LogTarget.CHAT).to((Player) sender).send(message);
            } else {
                messageLogger.error(message);
            }
        }
    }

    /**
     * Provides tab completion for the command.
     * 
     * @param sender the command sender
     * @param alias  the command alias
     * @param args   the command arguments
     * @return a list of tab completion suggestions
     */
    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
            @NotNull String[] args) {
        try {
            commandLogger.debug(
                    "Tab complete for: " + alias + " with args: " + Arrays.toString(args) + " by " + sender.getName());

            List<String> suggestions = commandManager.getSuggestions(alias, sender, Arrays.asList(args));

            List<String> filteredSuggestions = new ArrayList<>();
            for (String suggestion : suggestions) {
                if (suggestion != null && !suggestion.trim().isEmpty()) {
                    filteredSuggestions.add(suggestion);
                }
            }

            commandLogger.debug(
                    "Generated " + filteredSuggestions.size() + " suggestions: " + filteredSuggestions);
            return filteredSuggestions;
        } catch (Exception e) {
            messageLogger.error("Error generating tab completions for " + alias + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Sets the permission for this command.
     * 
     * @param permission the permission string
     */
    @Override
    public void setPermission(String permission) {
        super.setPermission(permission);
    }

}
