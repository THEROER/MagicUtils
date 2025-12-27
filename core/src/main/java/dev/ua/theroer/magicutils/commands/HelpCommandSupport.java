package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class HelpCommandSupport {
    private HelpCommandSupport() {
    }

    public record HelpResult(boolean success, List<String> lines, String errorMessage) {
        public static HelpResult success(List<String> lines) {
            return new HelpResult(true, lines != null ? lines : List.of(), null);
        }

        public static HelpResult failure(String message) {
            return new HelpResult(false, List.of(), message);
        }
    }

    public static HelpResult build(CommandManager<?> manager, String commandName, String subCommand) {
        if (manager == null) {
            return HelpResult.failure("Help unavailable (command manager not ready)");
        }

        if (commandName == null || commandName.isEmpty()) {
            return HelpResult.success(buildOverview(manager));
        }

        MagicCommand target = findCommand(manager, commandName);
        if (target == null) {
            return HelpResult.failure("Command not found: " + commandName);
        }

        CommandInfo info = MagicCommand.getCommandInfo(target.getClass()).orElse(null);
        if (info == null) {
            return HelpResult.failure("Command not found: " + commandName);
        }

        List<MagicCommand.SubCommandInfo> subs = MagicCommand.getSubCommands(target.getClass());
        List<String> lines = new ArrayList<>();
        lines.add("§e/" + info.name() + " §7- §f" + info.description());
        lines.add("§7Usage: §f" + manager.generateUsage(target, info));

        if (subCommand == null || subCommand.isEmpty()) {
            if (!subs.isEmpty()) {
                lines.add("§7Subcommands:");
                for (MagicCommand.SubCommandInfo sub : subs) {
                    List<CommandArgument> args = MagicCommand.getArguments(sub.method);
                    String argStr = manager.formatArguments(args);
                    StringBuilder line = new StringBuilder(" §f/")
                            .append(info.name())
                            .append(" ")
                            .append(sub.annotation.name());
                    if (!argStr.isEmpty()) {
                        line.append(" ").append(argStr);
                    }
                    if (!sub.annotation.description().isEmpty()) {
                        line.append(" §8- §7").append(sub.annotation.description());
                    }
                    lines.add(line.toString());
                }
            }
            return HelpResult.success(lines);
        }

        MagicCommand.SubCommandInfo match = subs.stream()
                .filter(sub -> sub.annotation.name().equalsIgnoreCase(subCommand))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return HelpResult.failure("Subcommand not found: " + subCommand);
        }

        List<CommandArgument> args = MagicCommand.getArguments(match.method);
        String argStr = manager.formatArguments(args);
        lines.add("§7Subcommand: §f" + match.annotation.name());
        StringBuilder usage = new StringBuilder("§7Usage: §f/")
                .append(info.name())
                .append(" ")
                .append(match.annotation.name());
        if (!argStr.isEmpty()) {
            usage.append(" ").append(argStr);
        }
        lines.add(usage.toString());

        return HelpResult.success(lines);
    }

    public static String[] getCommandSuggestions(CommandManager<?> manager) {
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

    private static List<String> buildOverview(CommandManager<?> manager) {
        Map<String, MagicCommand> unique = new LinkedHashMap<>();
        for (MagicCommand cmd : manager.getAll()) {
            CommandInfo info = MagicCommand.getCommandInfo(cmd.getClass()).orElse(null);
            if (info != null && !unique.containsKey(info.name())) {
                unique.put(info.name(), cmd);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("§eAvailable commands (" + unique.size() + "):");
        unique.forEach((name, cmd) -> {
            CommandInfo info = MagicCommand.getCommandInfo(cmd.getClass()).orElse(null);
            if (info == null) {
                return;
            }
            String usage = manager.generateUsage(cmd, info);
            lines.add(" §7" + usage + " §8- §f" + info.description());
        });
        lines.add("§7Use /mhelp <command> for details.");
        return lines;
    }

    private static MagicCommand findCommand(CommandManager<?> manager, String commandName) {
        String lookup = commandName.toLowerCase(Locale.ROOT);
        for (MagicCommand cmd : manager.getAll()) {
            CommandInfo info = MagicCommand.getCommandInfo(cmd.getClass()).orElse(null);
            if (info == null) {
                continue;
            }
            if (info.name().equalsIgnoreCase(lookup)) {
                return cmd;
            }
            for (String alias : info.aliases()) {
                if (alias.equalsIgnoreCase(lookup)) {
                    return cmd;
                }
            }
        }
        return null;
    }
}
