package dev.ua.theroer.magicutils.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.SubLogger;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Bukkit command wrapper for integrating custom command logic with Bukkit's command system.
 */
public class BukkitCommandWrapper extends Command {
    private static final SubLogger logger = Logger.getSubLogger("Commands", "[Commands]");
    private final CommandManager commandManager;

    @Getter @Setter
    private String detailedUsage;

    /**
     * Constructs a new BukkitCommandWrapper.
     * @param name the command name
     * @param commandManager the command manager
     * @param aliases the command aliases
     */
    public BukkitCommandWrapper(String name, CommandManager commandManager, List<String> aliases) {
        super(name);
        this.commandManager = commandManager;
        if (aliases != null && !aliases.isEmpty()) {
            this.setAliases(aliases);
        }
    }

    /**
     * Executes the command.
     * @param sender the command sender
     * @param commandLabel the command label
     * @param args the command arguments
     * @return true if the command was successful
     */
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        try {
            logger.debug("Executing command: " + commandLabel + " with args: " + Arrays.toString(args) + " by " + sender.getName());
            
            CommandResult result = commandManager.execute(commandLabel, sender, Arrays.asList(args));
            
            if (result.isSendMessage() && result.getMessage() != null && !result.getMessage().isEmpty()) {
                if (result.isSuccess()) {
                    Logger.success(sender instanceof Player ? (Player) sender : null, result.getMessage());
                } else {
                    Logger.error(sender instanceof Player ? (Player) sender : null, result.getMessage());
                }
            }
            
            return result.isSuccess();
        } catch (Exception e) {
            Logger.error("Error executing command " + commandLabel + ": " + e.getMessage());
            Logger.error(sender instanceof Player ? (Player) sender : null, "An internal error occurred while executing the command.");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Provides tab completion for the command.
     * @param sender the command sender
     * @param alias the command alias
     * @param args the command arguments
     * @return a list of tab completion suggestions
     */
    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        try {
            logger.debug("Tab complete for: " + alias + " with args: " + Arrays.toString(args) + " by " + sender.getName());
            
            List<String> suggestions = commandManager.getSuggestions(alias, sender, Arrays.asList(args));
            
            List<String> filteredSuggestions = new ArrayList<>();
            for (String suggestion : suggestions) {
                if (suggestion != null && !suggestion.trim().isEmpty()) {
                    filteredSuggestions.add(suggestion);
                }
            }
            
            logger.debug("Generated " + filteredSuggestions.size() + " suggestions: " + filteredSuggestions);
            return filteredSuggestions;
        } catch (Exception e) {
            Logger.error("Error generating tab completions for " + alias + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Sets the permission for this command.
     * @param permission the permission string
     */
    @Override
    public void setPermission(String permission) {
        super.setPermission(permission);
    }
    
    /**
     * Sets the permission message for this command.
     * @param permissionMessage the permission message
     */
    @Override
    public BukkitCommandWrapper setPermissionMessage(String permissionMessage) {
        super.permissionMessage(Component.text(permissionMessage));
        return this;
    }
}