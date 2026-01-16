package dev.ua.theroer.magicutils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;

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
                                 dev.ua.theroer.magicutils.Logger messageLogger) {
        super(name);
        this.commandManager = commandManager;
        this.commandLogger = commandLogger;
        this.messageLogger = messageLogger;
    }

    /**
     * Factory to avoid leaking 'this' before full initialisation.
     *
     * @param name           command name
     * @param commandManager manager to delegate execution
     * @param aliases        optional aliases
     * @param commandLogger  command logger
     * @param messageLogger  message logger
     * @return fully initialised wrapper
     */
    public static BukkitCommandWrapper create(String name,
                                              CommandManager<CommandSender> commandManager,
                                              List<String> aliases,
                                              PrefixedLogger commandLogger,
                                              dev.ua.theroer.magicutils.Logger messageLogger) {
        BukkitCommandWrapper wrapper = new BukkitCommandWrapper(name, commandManager, commandLogger, messageLogger);
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

            CommandResult result = commandManager.execute(commandLabel, sender, Arrays.asList(args));

            if (result.isSendMessage() && result.getMessage() != null && !result.getMessage().isEmpty()) {
                if (result.isSuccess()) {
                    if (sender instanceof Player) {
                        messageLogger.success().target(LogTarget.CHAT).to((Player) sender).send(result.getMessage());
                    } else {
                        messageLogger.success(result.getMessage());
                    }
                } else {
                    if (sender instanceof Player) {
                        messageLogger.error().target(LogTarget.CHAT).to((Player) sender).send(result.getMessage());
                    } else {
                        messageLogger.error(result.getMessage());
                    }
                }
            }

            return result.isSuccess();
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

    /**
     * Sets the permission message for this command.
     * Note: Permission messages do not work for player-executed commands since
     * 1.13.
     * 
     * @param permissionMessage the permission message
     */
    @Override
    public BukkitCommandWrapper setPermissionMessage(String permissionMessage) {
        super.permissionMessage(Component.text(permissionMessage));
        return this;
    }
}
