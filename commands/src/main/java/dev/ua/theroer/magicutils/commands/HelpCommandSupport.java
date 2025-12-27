package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.config.logger.HelpSettings;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.logger.LogBuilderCore;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.ColorUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class HelpCommandSupport {
    private static final String DEFAULT_HELP_COMMAND = "mhelp";
    private static final HelpStyle DEFAULT_STYLE = new HelpStyle(
            "#ff55ff",
            "gray",
            "white",
            "#ff55ff",
            "-----------------------------",
            7,
            8
    );

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

    public static <S> SubCommandSpec<S> createHelpSubCommand(LoggerCore logger,
                                                             Supplier<CommandManager<?>> managerSupplier) {
        return createHelpSubCommand("help", logger, managerSupplier);
    }

    public static <S> SubCommandSpec<S> createHelpSubCommand(String subCommandName,
                                                             LoggerCore logger,
                                                             Supplier<CommandManager<?>> managerSupplier) {
        String helpName = safeTrim(subCommandName);
        if (helpName == null) {
            helpName = "help";
        }
        final String helpNameFinal = helpName;
        Supplier<CommandManager<?>> supplier = managerSupplier != null ? managerSupplier : () -> null;

        return SubCommandSpec.<S>builder(helpNameFinal)
                .description("Shows available commands and usages")
                .permissionDefault(MagicPermissionDefault.TRUE)
                .argument(CommandArgument.builder("sender", MagicSender.class)
                        .sender(new AllowedSender[] { AllowedSender.ANY })
                        .build())
                .argument(CommandArgument.builder("command", String.class)
                        .optional()
                        .suggestions("@commands")
                        .build())
                .argument(CommandArgument.builder("subcommand", String.class)
                        .optional()
                        .build())
                .execute(execution -> {
                    MagicSender sender = execution.arg("sender", MagicSender.class);
                    if (sender == null) {
                        return CommandResult.failure("Sender unavailable");
                    }

                    CommandManager<?> manager = supplier.get();
                    String commandName = execution.arg("command", String.class);
                    String subCommand = execution.arg("subcommand", String.class);

                    CommandInfo info = manager != null ? manager.getCommandInfo(execution.command()) : null;
                    String rootCommand = info != null ? info.name() : execution.commandName();
                    String helpCommand = rootCommand + " " + helpNameFinal;

                    if (commandName != null && subCommand == null && manager != null
                            && findSubCommand(manager, execution.command(), commandName) != null) {
                        subCommand = commandName;
                        commandName = rootCommand;
                    }

                    HelpResult result = build(manager, commandName, subCommand, helpCommand, logger, sender);
                    if (!result.success()) {
                        return CommandResult.failure(result.errorMessage());
                    }

                    sendLines(logger, sender, result.lines());
                    return CommandResult.success(false, "");
                })
                .build();
    }

    public static HelpResult build(CommandManager<?> manager, String commandName, String subCommand) {
        return build(manager, commandName, subCommand, DEFAULT_HELP_COMMAND, null, null);
    }

    public static HelpResult build(CommandManager<?> manager, String commandName, String subCommand,
                                   String helpCommand) {
        return build(manager, commandName, subCommand, helpCommand, null, null);
    }

    public static HelpResult build(CommandManager<?> manager, String commandName, String subCommand,
                                   String helpCommand, LoggerCore logger) {
        return build(manager, commandName, subCommand, helpCommand, logger, null);
    }

    public static HelpResult build(CommandManager<?> manager, String commandName, String subCommand,
                                   String helpCommand, LoggerCore logger, MagicSender sender) {
        if (manager == null) {
            return HelpResult.failure("Help unavailable (command manager not ready)");
        }

        HelpStyle style = resolveStyle(logger);
        HelpContext context = new HelpContext(normalizeHelpCommand(helpCommand));
        HelpRequest request = parseRequest(manager, commandName, subCommand);
        if (request.detailTarget != null) {
            return HelpResult.success(buildDetails(manager, request.detailTarget, request.detailSub,
                    context, style, sender));
        }
        return HelpResult.success(buildOverview(manager, request.query, request.page, context, style, sender));
    }

    public static String[] getCommandSuggestions(CommandManager<?> manager) {
        if (manager == null) {
            return new String[0];
        }
        return manager.getAll().stream()
                .map(cmd -> resolveCommandInfo(manager, cmd))
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

    private static List<String> buildOverview(CommandManager<?> manager, String query, int page,
                                              HelpContext context, HelpStyle style, MagicSender sender) {
        List<HelpEntry> entries = buildEntries(manager, sender);
        List<HelpEntry> filtered = filterEntries(entries, query);
        int pageSize = style.pageSize();
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) pageSize));
        int safePage = clamp(page, 1, totalPages);

        int start = (safePage - 1) * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        List<HelpEntry> pageEntries = filtered.subList(start, end);

        List<String> lines = new ArrayList<>();
        lines.addAll(buildHeaderLines("Help", safePage, totalPages, style));
        lines.add(buildQueryLine(query, style));
        lines.add(color(style.mutedTag(), "|- Available commands:"));

        if (pageEntries.isEmpty()) {
            lines.add(color(style.mutedTag(), "|- No commands found."));
        } else {
            for (int i = 0; i < pageEntries.size(); i++) {
                HelpEntry entry = pageEntries.get(i);
                boolean last = i == pageEntries.size() - 1;
                lines.add(buildEntryLine(entry, last, context, style));
            }
        }

        if (totalPages > 1) {
            lines.add(buildFooter(query, safePage, totalPages, context, style));
        } else {
            lines.add(color(style.lineTag(), style.lineText()));
        }

        return lines;
    }

    private static MagicCommand findCommand(CommandManager<?> manager, String commandName) {
        String lookup = commandName.toLowerCase(Locale.ROOT);
        for (MagicCommand cmd : manager.getAll()) {
            CommandInfo info = resolveCommandInfo(manager, cmd);
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

    private static HelpRequest parseRequest(CommandManager<?> manager, String commandName, String subCommand) {
        String first = safeTrim(commandName);
        String second = safeTrim(subCommand);

        if (first == null) {
            return new HelpRequest(null, null, "", 1);
        }

        Integer page = parsePage(first);
        if (page != null) {
            String query = second != null ? second : "";
            return new HelpRequest(null, null, query, page);
        }

        MagicCommand target = findCommand(manager, first);
        if (target != null) {
            if (second != null) {
                CommandManager.CommandAction<?> sub = findSubCommand(manager, target, second);
                if (sub != null) {
                    return new HelpRequest(target, sub, "", 1);
                }
                Integer subPage = parsePage(second);
                if (subPage != null) {
                    return new HelpRequest(null, null, first, subPage);
                }
            }
            return new HelpRequest(target, null, "", 1);
        }

        Integer secondPage = parsePage(second);
        if (secondPage != null) {
            return new HelpRequest(null, null, first, secondPage);
        }

        String query = second != null ? first + " " + second : first;
        return new HelpRequest(null, null, query, 1);
    }

    private static CommandManager.CommandAction<?> findSubCommand(CommandManager<?> manager,
                                                                  MagicCommand command,
                                                                  String name) {
        if (manager == null || command == null || name == null || name.isEmpty()) {
            return null;
        }
        for (CommandManager.CommandAction<?> sub : getSubCommandActions(manager, command)) {
            if (sub.name().equalsIgnoreCase(name)) {
                return sub;
            }
            for (String alias : sub.aliases()) {
                if (alias.equalsIgnoreCase(name)) {
                    return sub;
                }
            }
        }
        return null;
    }

    private static List<String> buildDetails(CommandManager<?> manager, MagicCommand target,
                                             CommandManager.CommandAction<?> subInfo, HelpContext context,
                                             HelpStyle style, MagicSender sender) {
        CommandInfo info = resolveCommandInfo(manager, target);
        if (info == null) {
            return List.of(color(style.mutedTag(), "Command not found."));
        }

        String baseCommandName = baseCommandName(info);
        CommandManager.CommandAction<?> directAction = getDirectAction(manager, target, info);
        boolean directAllowed = directAction != null && canViewDirect(manager, sender, info, baseCommandName);
        List<CommandManager.CommandAction<?>> subs = getSubCommandActions(manager, target);
        List<CommandManager.CommandAction<?>> allowedSubs = filterSubCommands(manager, sender, baseCommandName, subs);

        if (subInfo != null && !canViewSubCommand(manager, sender, baseCommandName, subInfo)) {
            return List.of(color(style.mutedTag(), "Command not found."));
        }
        if (subInfo == null && !directAllowed && allowedSubs.isEmpty()) {
            return List.of(color(style.mutedTag(), "Command not found."));
        }

        List<String> lines = new ArrayList<>();
        lines.addAll(buildHeaderLines("Help", 0, 0, style));

        if (subInfo != null) {
            lines.addAll(buildSubCommandDetails(manager, info, subInfo, style, sender, baseCommandName));
        } else {
            lines.addAll(buildCommandDetails(manager, info, context, style, sender,
                    baseCommandName, directAction, directAllowed, allowedSubs));
        }

        lines.add(color(style.lineTag(), style.lineText()));
        return lines;
    }

    private static List<String> buildCommandDetails(CommandManager<?> manager, CommandInfo info,
                                                    HelpContext context, HelpStyle style, MagicSender sender,
                                                    String baseCommandName, CommandManager.CommandAction<?> directAction,
                                                    boolean directAllowed,
                                                    List<CommandManager.CommandAction<?>> allowedSubs) {
        List<String> lines = new ArrayList<>();
        List<CommandArgument> directArgs = directAction != null
                ? filterVisibleArguments(manager, sender, baseCommandName, null, directAction.arguments())
                : List.of();
        String usage = buildCommandUsage(manager, info, directArgs, directAllowed, allowedSubs);
        lines.add(color(style.mutedTag(), "Command: ") + color(style.textTag(), styleUsage(usage, style)));
        lines.add(color(style.mutedTag(), "Description: ") + color(style.textTag(), safeDescription(info.description())));

        List<String> commandAliases = normalizeAliases(info.aliases());
        if (!commandAliases.isEmpty()) {
            lines.add(color(style.mutedTag(), "Aliases: ") + color(style.textTag(), joinValues(commandAliases)));
        }

        if (directAction != null && directAllowed) {
            appendArguments(lines, directAction.arguments(), manager, style, sender, baseCommandName, null);
        }

        if (!allowedSubs.isEmpty()) {
            lines.add(color(style.mutedTag(), "Subcommands:"));
            for (int i = 0; i < allowedSubs.size(); i++) {
                CommandManager.CommandAction<?> sub = allowedSubs.get(i);
                List<CommandArgument> subArgs = filterVisibleArguments(manager, sender, baseCommandName,
                        sub.name(), sub.arguments());
                String subUsage = styleUsage(buildUsage(manager, info, sub.name(), subArgs), style);
                String prefix = i == allowedSubs.size() - 1 ? "`-" : "|-";
                String hover = hoverText(sub.description());
                String click = buildHelpCommand(context, info.name(), sub.name());
                String line = color(style.mutedTag(), prefix + " ")
                        + clickable(subUsage, hover, click, style);
                lines.add(line);
            }
        }

        return lines;
    }

    private static List<String> buildSubCommandDetails(CommandManager<?> manager, CommandInfo info,
                                                       CommandManager.CommandAction<?> subInfo, HelpStyle style,
                                                       MagicSender sender, String baseCommandName) {
        List<String> lines = new ArrayList<>();
        List<CommandArgument> args = subInfo != null ? subInfo.arguments() : List.of();
        List<CommandArgument> visibleArgs = filterVisibleArguments(manager, sender, baseCommandName,
                subInfo != null ? subInfo.name() : null, args);
        String usage = buildUsage(manager, info, subInfo != null ? subInfo.name() : null, visibleArgs);
        lines.add(color(style.mutedTag(), "Command: ") + color(style.textTag(), styleUsage(usage, style)));
        lines.add(color(style.mutedTag(), "Description: ") + color(style.textTag(),
                safeDescription(subInfo != null ? subInfo.description() : "")));
        List<String> aliases = subInfo != null ? normalizeAliases(subInfo.aliases()) : List.of();
        if (!aliases.isEmpty()) {
            lines.add(color(style.mutedTag(), "Aliases: ") + color(style.textTag(), joinValues(aliases)));
        }
        appendArguments(lines, args, manager, style, sender, baseCommandName, subInfo != null ? subInfo.name() : null);
        return lines;
    }

    private static void appendArguments(List<String> lines, List<CommandArgument> args,
                                        CommandManager<?> manager, HelpStyle style,
                                        MagicSender sender, String baseCommandName,
                                        String subCommandName) {
        if (args == null || args.isEmpty()) {
            return;
        }
        List<CommandArgument> visible = filterVisibleArguments(manager, sender, baseCommandName, subCommandName, args);
        if (visible.isEmpty()) {
            return;
        }
        lines.add(color(style.mutedTag(), "Arguments:"));
        for (int i = 0; i < visible.size(); i++) {
            CommandArgument arg = visible.get(i);
            String prefix = i == visible.size() - 1 ? "`-" : "|-";
            boolean optional = arg.isOptional() || arg.getDefaultValue() != null;
            String label = argumentLabel(arg);
            String suffix = optional ? " " + color(style.primaryTag(), "(Optional)") : "";
            String defaultValue = arg.getDefaultValue();
            if (defaultValue != null) {
                String shown = defaultValue.isEmpty() ? "\"\"" : escapeMiniText(defaultValue);
                suffix += " " + color(style.mutedTag(), "(Default: " + shown + ")");
            }
            List<String> enumValues = getEnumValues(arg);
            if (!enumValues.isEmpty() && enumValues.size() <= style.maxEnumValues()) {
                suffix += " " + color(style.mutedTag(), "(Values: " + joinValues(enumValues) + ")");
            }
            lines.add(color(style.mutedTag(), prefix + " ") + color(style.textTag(), label) + suffix);
        }
    }

    private static List<HelpEntry> buildEntries(CommandManager<?> manager, MagicSender sender) {
        Map<String, MagicCommand> unique = new LinkedHashMap<>();
        for (MagicCommand cmd : manager.getAll()) {
            CommandInfo info = resolveCommandInfo(manager, cmd);
            if (info != null && !unique.containsKey(info.name())) {
                unique.put(info.name(), cmd);
            }
        }

        List<HelpEntry> entries = new ArrayList<>();
        for (MagicCommand cmd : unique.values()) {
            CommandInfo info = resolveCommandInfo(manager, cmd);
            if (info == null) {
                continue;
            }
            String baseCommandName = baseCommandName(info);

            CommandManager.CommandAction<?> directAction = getDirectAction(manager, cmd, info);
            if (directAction != null && canViewDirect(manager, sender, info, baseCommandName)) {
                List<CommandArgument> directArgs = filterVisibleArguments(manager, sender, baseCommandName,
                        null, directAction.arguments());
                String usage = buildUsage(manager, info, null, directArgs);
                entries.add(new HelpEntry(usage, info.description(), info.name(), null,
                        normalizeAliases(info.aliases())));
            }

            for (CommandManager.CommandAction<?> sub : getSubCommandActions(manager, cmd)) {
                if (!canViewSubCommand(manager, sender, baseCommandName, sub)) {
                    continue;
                }
                List<CommandArgument> subArgs = filterVisibleArguments(manager, sender, baseCommandName,
                        sub.name(), sub.arguments());
                String usage = buildUsage(manager, info, sub.name(), subArgs);
                entries.add(new HelpEntry(usage, sub.description(), info.name(), sub.name(),
                        normalizeAliases(sub.aliases())));
            }
        }

        entries.sort(Comparator.comparing(HelpEntry::usage, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static List<HelpEntry> filterEntries(List<HelpEntry> entries, String query) {
        if (query == null || query.isEmpty()) {
            return entries;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<HelpEntry> filtered = new ArrayList<>();
        for (HelpEntry entry : entries) {
            String desc = entry.description != null ? entry.description : "";
            String command = entry.command != null ? entry.command : "";
            String sub = entry.subcommand != null ? entry.subcommand : "";
            String aliases = entry.aliases != null ? String.join(" ", entry.aliases) : "";
            if (entry.usage.toLowerCase(Locale.ROOT).contains(needle)
                    || desc.toLowerCase(Locale.ROOT).contains(needle)
                    || command.toLowerCase(Locale.ROOT).contains(needle)
                    || sub.toLowerCase(Locale.ROOT).contains(needle)
                    || aliases.toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static List<String> buildHeaderLines(String title, int page, int totalPages, HelpStyle style) {
        List<String> lines = new ArrayList<>();
        lines.add(color(style.lineTag(), style.lineText()));
        String heading = title;
        if (page > 0 && totalPages > 0) {
            heading += " (" + page + "/" + totalPages + ")";
        }
        lines.add(color(style.textTag(), heading));
        lines.add(color(style.lineTag(), style.lineText()));
        return lines;
    }

    private static String buildFooter(String query, int page, int totalPages, HelpContext context, HelpStyle style) {
        String prev = navButton("[-]", page > 1, query, page - 1, "Previous page", context, style);
        String next = navButton("[+]", page < totalPages, query, page + 1, "Next page", context, style);
        return prev + " " + color(style.mutedTag(), "Page " + page + "/" + totalPages) + " " + next;
    }

    private static String navButton(String label, boolean enabled, String query, int page, String hover,
                                    HelpContext context, HelpStyle style) {
        if (!enabled) {
            return color(style.mutedTag(), label);
        }
        String command = buildHelpCommand(context, query, page);
        if (command == null) {
            return color(style.mutedTag(), label);
        }
        return clickable(label, hover, command, style);
    }

    private static String buildQueryLine(String query, HelpStyle style) {
        String safe = query == null ? "" : query;
        return color(style.mutedTag(), "Showing search results for query: ")
                + color(style.textTag(), "\"" + escapeMiniText(safe) + "\"");
    }

    private static String buildEntryLine(HelpEntry entry, boolean last, HelpContext context, HelpStyle style) {
        String prefix = last ? "`-" : "|-";
        String hover = hoverText(entry.description);
        String click = buildHelpCommand(context, entry.command, entry.subcommand);
        String usage = styleUsage(entry.usage, style);
        String line = color(style.mutedTag(), prefix + " ") + clickable(usage, hover, click, style);
        if (entry.subcommand == null && entry.aliases != null && !entry.aliases.isEmpty()) {
            line += " " + color(style.mutedTag(), "(aliases: " + joinValues(entry.aliases) + ")");
        }
        return line;
    }

    private static String clickable(String text, String hover, String command, HelpStyle style) {
        String hoverText = escapeMiniAttribute(hover);
        String clickCommand = escapeMiniAttribute(command);
        return "<click:run_command:\"" + clickCommand + "\">"
                + "<hover:show_text:\"" + hoverText + "\">"
                + color(style.textTag(), text)
                + "</hover></click>";
    }

    private static String buildHelpCommand(HelpContext context, String command, String sub) {
        String base = context != null ? context.helpCommand : DEFAULT_HELP_COMMAND;
        if (command == null || command.isEmpty()) {
            return "/" + base;
        }
        StringBuilder builder = new StringBuilder("/").append(base).append(" ").append(command);
        if (sub != null && !sub.isEmpty()) {
            builder.append(" ").append(sub);
        }
        return builder.toString();
    }

    private static String buildHelpCommand(HelpContext context, String query, int page) {
        String safeQuery = safeTrim(query);
        String base = context != null ? context.helpCommand : DEFAULT_HELP_COMMAND;
        StringBuilder builder = new StringBuilder("/").append(base);
        if (safeQuery != null) {
            if (safeQuery.contains(" ")) {
                return null;
            }
            builder.append(" ").append(safeQuery);
        }
        builder.append(" ").append(page);
        return builder.toString();
    }

    private static String buildUsage(CommandManager<?> manager, CommandInfo info,
                                     String subCommandName, List<CommandArgument> arguments) {
        StringBuilder usage = new StringBuilder("/").append(info.name());
        if (subCommandName != null && !subCommandName.isEmpty()) {
            usage.append(" ").append(subCommandName);
        }
        String argStr = manager.formatArguments(arguments);
        if (!argStr.isEmpty()) {
            usage.append(" ").append(argStr);
        }
        return usage.toString();
    }

    private static CommandInfo resolveCommandInfo(CommandManager<?> manager, MagicCommand command) {
        if (command == null) {
            return null;
        }
        if (manager != null) {
            CommandInfo info = manager.getCommandInfo(command);
            if (info != null) {
                return info;
            }
        }
        return MagicCommand.getCommandInfo(command.getClass()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<CommandManager.CommandAction<?>> getSubCommandActions(CommandManager<?> manager,
                                                                              MagicCommand command) {
        if (manager == null || command == null) {
            return List.of();
        }
        return (List<CommandManager.CommandAction<?>>) (List<?>) manager.getSubCommandActions(command);
    }

    private static CommandManager.CommandAction<?> getDirectAction(CommandManager<?> manager,
                                                                   MagicCommand command,
                                                                   CommandInfo info) {
        if (manager == null || command == null || info == null) {
            return null;
        }
        return (CommandManager.CommandAction<?>) manager.getDirectAction(command, info);
    }

    private static String baseCommandName(CommandInfo info) {
        if (info == null) {
            return "";
        }
        String name = info.name();
        return name != null ? name.toLowerCase(Locale.ROOT) : "";
    }

    private static boolean canViewDirect(CommandManager<?> manager, MagicSender sender, CommandInfo info,
                                         String baseCommandName) {
        if (manager == null || sender == null || info == null) {
            return true;
        }
        String permission = manager.resolvePermission(info.permission(), "commands." + baseCommandName);
        return manager.hasPermissionForHelp(sender, permission, info.permissionDefault(), info.description())
                || hasArgumentPermissionOverride(manager, sender, baseCommandName, null);
    }

    private static boolean canViewSubCommand(CommandManager<?> manager, MagicSender sender, String baseCommandName,
                                             CommandManager.CommandAction<?> subInfo) {
        if (manager == null || sender == null || subInfo == null) {
            return true;
        }
        String permission = manager.resolvePermission(subInfo.permission(),
                "commands." + baseCommandName + ".subcommand." + subInfo.name());
        return manager.hasPermissionForHelp(sender, permission, subInfo.permissionDefault(), subInfo.description())
                || hasArgumentPermissionOverride(manager, sender, baseCommandName, subInfo.name());
    }

    private static List<CommandManager.CommandAction<?>> filterSubCommands(CommandManager<?> manager,
                                                                           MagicSender sender,
                                                                           String baseCommandName,
                                                                           List<CommandManager.CommandAction<?>> subs) {
        if (subs == null || subs.isEmpty()) {
            return List.of();
        }
        if (manager == null || sender == null) {
            return new ArrayList<>(subs);
        }
        List<CommandManager.CommandAction<?>> allowed = new ArrayList<>();
        for (CommandManager.CommandAction<?> sub : subs) {
            if (canViewSubCommand(manager, sender, baseCommandName, sub)) {
                allowed.add(sub);
            }
        }
        return allowed;
    }

    private static List<CommandArgument> filterVisibleArguments(CommandManager<?> manager,
                                                                MagicSender sender,
                                                                String baseCommandName,
                                                                String subCommandName,
                                                                List<CommandArgument> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        List<CommandArgument> visible = new ArrayList<>();
        for (CommandArgument arg : args) {
            if (arg == null) {
                continue;
            }
            boolean senderArg = arg.isSenderParameter();
            if (!senderArg && manager != null) {
                senderArg = manager.isSenderArgument(arg);
            }
            if (senderArg) {
                continue;
            }
            if (!canViewArgument(manager, sender, baseCommandName, subCommandName, arg)) {
                continue;
            }
            visible.add(arg);
        }
        return visible;
    }

    private static boolean canViewArgument(CommandManager<?> manager, MagicSender sender, String baseCommandName,
                                           String subCommandName, CommandArgument argument) {
        if (manager == null || sender == null || argument == null) {
            return true;
        }
        if (!argument.hasPermission()) {
            return true;
        }
        String fallback = manager.buildArgumentPermission(baseCommandName, subCommandName, argument);
        String resolved = manager.resolvePermission(argument.getPermission(), fallback);
        String argName = argument.getName();
        if (argName == null || argName.isEmpty()) {
            argName = argumentLabel(argument);
        }
        String description = "Argument " + argName + " for /" + baseCommandName
                + (subCommandName != null && !subCommandName.isEmpty() ? " " + subCommandName : "");
        return manager.hasPermissionForHelp(sender, resolved, argument.getPermissionDefault(), description);
    }

    private static boolean hasArgumentPermissionOverride(CommandManager<?> manager, MagicSender sender,
                                                         String baseCommandName, String subCommandName) {
        if (manager == null || sender == null) {
            return false;
        }
        String prefix = "commands." + baseCommandName;
        if (subCommandName != null && !subCommandName.isEmpty()) {
            prefix += ".subcommand." + subCommandName;
        }
        String argPrefix = manager.resolvePermission(null, prefix + ".argument");
        String argPrefixNoSegment = manager.resolvePermission(null, prefix);
        return manager.hasPermissionByPrefixForHelp(sender, argPrefix, argPrefixNoSegment);
    }

    private static String buildCommandUsage(CommandManager<?> manager, CommandInfo info,
                                            List<CommandArgument> directArgs, boolean directAllowed,
                                            List<CommandManager.CommandAction<?>> allowedSubs) {
        String base = "/" + info.name();
        String directUsage = null;
        if (directAllowed) {
            String args = manager != null ? manager.formatArguments(directArgs) : "";
            directUsage = args.isEmpty() ? base : base + " " + args;
        }
        String subsUsage = null;
        if (allowedSubs != null && !allowedSubs.isEmpty()) {
            String joined = allowedSubs.stream()
                    .map(CommandManager.CommandAction::name)
                    .collect(java.util.stream.Collectors.joining(" | "));
            subsUsage = base + " <" + joined + ">";
        }
        if (directUsage != null && subsUsage != null) {
            return directUsage + " OR " + subsUsage;
        }
        if (directUsage != null) {
            return directUsage;
        }
        if (subsUsage != null) {
            return subsUsage;
        }
        return base;
    }

    private static String styleUsage(String usage, HelpStyle style) {
        if (usage == null || usage.isEmpty()) {
            return usage;
        }
        String safeUsage = escapeMiniText(usage);
        StringBuilder out = new StringBuilder();
        boolean optional = false;
        for (int i = 0; i < safeUsage.length(); i++) {
            char ch = safeUsage.charAt(i);
            if (ch == '[') {
                if (!optional) {
                    out.append(openTag(style.primaryTag()));
                    optional = true;
                }
                out.append(ch);
            } else if (ch == ']') {
                out.append(ch);
                if (optional) {
                    out.append(closeTag(style.primaryTag()));
                    optional = false;
                }
            } else {
                out.append(ch);
            }
        }
        if (optional) {
            out.append(closeTag(style.primaryTag()));
        }
        return out.toString();
    }

    private static String argumentLabel(CommandArgument argument) {
        String name = argument.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return argument.getType().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeAliases(String[] aliases) {
        if (aliases == null || aliases.length == 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>(aliases.length);
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String trimmed = alias.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> normalizeAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(aliases.size());
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String trimmed = alias.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> getEnumValues(CommandArgument argument) {
        if (argument == null) {
            return List.of();
        }
        Class<?> type = argument.getType();
        if (type == null || !type.isEnum()) {
            return List.of();
        }
        Object[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>(constants.length);
        for (Object constant : constants) {
            if (constant instanceof Enum<?> e) {
                values.add(e.name().toLowerCase(Locale.ROOT));
            } else if (constant != null) {
                values.add(constant.toString().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private static String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(escapeMiniText(values.get(i)));
        }
        return out.toString();
    }

    private static String safeDescription(String description) {
        if (description == null || description.isEmpty()) {
            return "No description";
        }
        return description;
    }

    private static String hoverText(String description) {
        if (description == null || description.isEmpty()) {
            return "Click to show help for this command";
        }
        return description;
    }

    private static String normalizeHelpCommand(String helpCommand) {
        if (helpCommand == null) {
            return DEFAULT_HELP_COMMAND;
        }
        String trimmed = helpCommand.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_HELP_COMMAND;
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty()) {
            return DEFAULT_HELP_COMMAND;
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    private static String safeTrim(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer parsePage(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void sendLines(LoggerCore logger, MagicSender sender, List<String> lines) {
        if (sender == null || lines == null || lines.isEmpty()) {
            return;
        }
        Audience audience = sender.audience();
        for (String line : lines) {
            if (logger != null) {
                new LogBuilderCore(logger, LogLevel.INFO).noPrefix().to(audience).send(line);
            } else if (audience != null) {
                audience.send(MessageParser.parseSmart(line));
            }
        }
    }

    private static String color(String tag, String text) {
        if (text == null) {
            return "";
        }
        if (tag == null || tag.isEmpty()) {
            return text;
        }
        return openTag(tag) + text + closeTag(tag);
    }

    private static String openTag(String tag) {
        String trimmed = tag.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed;
        }
        return "<" + trimmed + ">";
    }

    private static String closeTag(String tag) {
        String trimmed = tag.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            String name = inner.split(":", 2)[0];
            return "</" + name + ">";
        }
        return "</" + trimmed + ">";
    }

    private static String escapeMiniText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = text.replace("\\", "\\\\");
        escaped = escaped.replace("<", "\\<").replace(">", "\\>");
        return escaped;
    }

    private static String escapeMiniAttribute(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = text.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("<", "\\<").replace(">", "\\>");
        return escaped;
    }

    private static String normalizeTag(String tag, String fallback) {
        if (tag == null) {
            return fallback;
        }
        String trimmed = tag.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String safeLine(String line, String fallback) {
        if (line == null) {
            return fallback;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String firstColor(String[] colors, String fallback) {
        if (colors == null || colors.length == 0) {
            return fallback;
        }
        String primary = colors[0];
        return normalizeTag(primary, fallback);
    }

    private static String mutedFrom(String primary, String fallback) {
        if (primary == null) {
            return fallback;
        }
        String trimmed = primary.trim();
        if (trimmed.startsWith("#") && trimmed.length() == 7) {
            return ColorUtils.adjustBrightness(trimmed, 0.7f);
        }
        return fallback;
    }

    private static HelpStyle resolveStyle(LoggerCore logger) {
        HelpStyle base = DEFAULT_STYLE;
        if (logger == null) {
            return base;
        }
        LoggerConfig config = logger.getConfig();
        if (config == null) {
            return base;
        }
        HelpSettings help = config.getHelp();
        if (help == null) {
            return base;
        }

        int pageSize = help.getPageSize() > 0 ? help.getPageSize() : base.pageSize();
        int maxEnumValues = help.getMaxEnumValues() > 0 ? help.getMaxEnumValues() : base.maxEnumValues();
        String lineText = safeLine(help.getLine(), base.lineText());

        if (help.isUseLoggerColors()) {
            String[] palette = config.resolveColors(LogLevel.INFO, false);
            String primary = firstColor(palette, base.primaryTag());
            String muted = mutedFrom(primary, base.mutedTag());
            String text = normalizeTag(help.getTextColor(), base.textTag());
            String lineTag = palette != null && palette.length > 1
                    ? ColorUtils.createGradientTag(palette)
                    : primary;
            return new HelpStyle(primary, muted, text, lineTag, lineText, pageSize, maxEnumValues);
        }

        String primary = normalizeTag(help.getPrimaryColor(), base.primaryTag());
        String muted = normalizeTag(help.getMutedColor(), base.mutedTag());
        String text = normalizeTag(help.getTextColor(), base.textTag());
        return new HelpStyle(primary, muted, text, primary, lineText, pageSize, maxEnumValues);
    }

    private record HelpRequest(MagicCommand detailTarget,
                               CommandManager.CommandAction<?> detailSub,
                               String query,
                               int page) {
    }

    private record HelpEntry(String usage, String description, String command, String subcommand,
                             List<String> aliases) {
    }

    private record HelpContext(String helpCommand) {
    }

    private record HelpStyle(String primaryTag,
                             String mutedTag,
                             String textTag,
                             String lineTag,
                             String lineText,
                             int pageSize,
                             int maxEnumValues) {
    }
}
