package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandArgument;
import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;

/**
 * Simple help command that lists all registered commands and their subcommands.
 */
@CommandInfo(
        name = "mhelp",
        description = "Shows available commands and usages",
        aliases = {"help"},
        permissionDefault = MagicPermissionDefault.TRUE
)
public class HelpCommand extends MagicCommand {

    /** Default constructor. */
    public HelpCommand() {
    }

    /**
     * Execute help: list commands or details for a specific one.
     *
     * @param sender command sender
     * @param commandName optional command name
     * @param subCommand optional subcommand
     * @return result
     */
    public CommandResult execute(CommandSender sender,
                                 @OptionalArgument @Suggest("getCommandSuggestions") String commandName,
                                 @OptionalArgument String subCommand) {
        CommandManager manager = CommandRegistry.getCommandManager();
        if (manager == null) {
            return CommandResult.failure("Help unavailable (command manager not ready)");
        }

        if (commandName == null || commandName.isEmpty()) {
            Map<String, MagicCommand> unique = new LinkedHashMap<>();
            for (MagicCommand cmd : manager.getAll()) {
                CommandInfo info = MagicCommand.getCommandInfo(cmd.getClass()).orElse(null);
                if (info != null && !unique.containsKey(info.name())) {
                    unique.put(info.name(), cmd);
                }
            }

            Logger.noPrefix().to(sender).send("§eAvailable commands (" + unique.size() + "):");
            unique.forEach((name, cmd) -> {
                CommandInfo info = MagicCommand.getCommandInfo(cmd.getClass()).orElse(null);
                if (info == null) return;
                String usage = manager.generateUsage(cmd, info);
                Logger.noPrefix().to(sender).send(" §7" + usage + " §8- §f" + info.description());
            });
            Logger.noPrefix().to(sender).send("§7Use /mhelp <command> for details.");
            return CommandResult.success(false, "");
        }

        String lookup = commandName.toLowerCase(Locale.ROOT);
        MagicCommand target = manager.getAll().stream()
                .filter(cmd -> {
                    CommandInfo info = MagicCommand.getCommandInfo(cmd.getClass()).orElse(null);
                    if (info == null) return false;
                    if (info.name().equalsIgnoreCase(lookup)) return true;
                    for (String alias : info.aliases()) {
                        if (alias.equalsIgnoreCase(lookup)) return true;
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);

        if (target == null) {
            return CommandResult.failure("Command not found: " + commandName);
        }

        CommandInfo info = MagicCommand.getCommandInfo(target.getClass()).orElse(null);
        if (info == null) {
            return CommandResult.failure("Command not found: " + commandName);
        }

        List<MagicCommand.SubCommandInfo> subs = MagicCommand.getSubCommands(target.getClass());
        Logger.noPrefix().to(sender).send("§e/" + info.name() + " §7- §f" + info.description());
        Logger.noPrefix().to(sender).send("§7Usage: §f" + manager.generateUsage(target, info));

        if (subCommand == null || subCommand.isEmpty()) {
            if (!subs.isEmpty()) {
                Logger.noPrefix().to(sender).send("§7Subcommands:");
                for (MagicCommand.SubCommandInfo sub : subs) {
                    List<CommandArgument> args = MagicCommand.getArguments(sub.method);
                    String argStr = formatArgs(args);
                    Logger.noPrefix().to(sender).send(" §f/" + info.name() + " " + sub.annotation.name() +
                            (argStr.isEmpty() ? "" : " " + argStr) +
                            (sub.annotation.description().isEmpty() ? "" : " §8- §7" + sub.annotation.description()));
                }
            }
        } else {
            MagicCommand.SubCommandInfo match = subs.stream()
                    .filter(s -> s.annotation.name().equalsIgnoreCase(subCommand))
                    .findFirst().orElse(null);
            if (match == null) {
                return CommandResult.failure("Subcommand not found: " + subCommand);
            }
            List<CommandArgument> args = MagicCommand.getArguments(match.method);
            String argStr = formatArgs(args);
            Logger.noPrefix().to(sender).send("§7Subcommand: §f" + match.annotation.name());
            Logger.noPrefix().to(sender).send("§7Usage: §f/" + info.name() + " " + match.annotation.name() +
                    (argStr.isEmpty() ? "" : " " + argStr));
        }

        return CommandResult.success(false, "");
    }

    private String formatArgs(List<CommandArgument> args) {
        return args.stream()
                .filter(arg -> !arg.getType().equals(CommandSender.class))
                .map(arg -> (arg.isOptional() ? "[" : "<") + arg.getName() + (arg.isOptional() ? "]" : ">"))
                .collect(Collectors.joining(" "));
    }

    /**
     * Suggestions for command names.
     *
     * @return array of command/alias names
     */
    public String[] getCommandSuggestions() {
        CommandManager manager = CommandRegistry.getCommandManager();
        if (manager == null) {
            return new String[0];
        }
        return manager.getAll().stream()
                .map(cmd -> MagicCommand.getCommandInfo(cmd.getClass()).orElse(null))
                .filter(Objects::nonNull)
                .flatMap(info -> {
                    List<String> names = new ArrayList<>();
                    names.add(info.name());
                    for (String alias : info.aliases()) {
                        names.add(alias);
                    }
                    return names.stream();
                })
                .distinct()
                .toArray(String[]::new);
    }
}
