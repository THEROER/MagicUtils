package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Renders usage strings for commands, subcommands and argument lists
 * ({@code <required>}, {@code [optional]}, {@code --option <value>}).
 *
 * @param <S> sender type
 */
final class CommandUsageFormatter<S> {
    private final CommandDescriptionResolver descriptions;
    private final Predicate<CommandArgument> senderArgument;

    CommandUsageFormatter(CommandDescriptionResolver descriptions, Predicate<CommandArgument> senderArgument) {
        this.descriptions = descriptions;
        this.senderArgument = senderArgument;
    }

    String generateUsage(CommandInfo info, List<CommandAction<S>> subCommands, CommandAction<S> directAction) {
        StringBuilder usage = new StringBuilder();
        usage.append("/").append(info.name());

        // If command has direct execute handler and no subcommands
        if (directAction != null && subCommands.isEmpty()) {
            appendArgumentsToUsage(usage, directAction.arguments());
        }
        // If command has only subcommands
        else if (!subCommands.isEmpty() && directAction == null) {
            usage.append(" <");
            usage.append(getTopLevelSubCommandNames(subCommands).stream()
                    .collect(Collectors.joining(" | ")));
            usage.append(">");
        }
        // If command has both execute handler and subcommands
        else if (directAction != null && !subCommands.isEmpty()) {
            List<CommandArgument> arguments = directAction.arguments();

            // Show direct arguments as optional since subcommands are available
            if (!arguments.isEmpty()) {
                appendArgumentsToUsage(usage, arguments, true);
            }

            usage.append(" OR /").append(info.name()).append(" <");
            usage.append(getTopLevelSubCommandNames(subCommands).stream()
                    .collect(Collectors.joining(" | ")));
            usage.append(">");
        }

        return usage.toString();
    }

    List<String> generateSubCommandUsages(CommandInfo info, List<CommandAction<S>> subCommands) {
        List<String> usages = new ArrayList<>();

        for (CommandAction<S> subInfo : subCommands) {
            StringBuilder usage = new StringBuilder();
            usage.append("/").append(info.name()).append(" ").append(subInfo.fullPath());

            appendArgumentsToUsage(usage, subInfo.arguments());

            String description = descriptions.resolveActionDescription(info.name(), subInfo);
            if (description != null && !description.isEmpty()) {
                usage.append(" - ").append(description);
            }

            usages.add(usage.toString());
        }

        return usages;
    }

    String formatArguments(List<CommandArgument> arguments, boolean forceOptional) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        StringBuilder usage = new StringBuilder();
        appendArgumentsToUsage(usage, arguments, forceOptional);
        return usage.toString().trim();
    }

    /**
     * Appends arguments to usage string.
     * 
     * @param usage     the usage string builder
     * @param arguments the list of arguments
     */
    private void appendArgumentsToUsage(StringBuilder usage, List<CommandArgument> arguments) {
        appendArgumentsToUsage(usage, arguments, false);
    }

    /**
     * Appends arguments to usage string.
     * 
     * @param usage         the usage string builder
     * @param arguments     the list of arguments
     * @param forceOptional whether to force all arguments to be displayed as
     *                      optional
     */
    private void appendArgumentsToUsage(StringBuilder usage, List<CommandArgument> arguments, boolean forceOptional) {
        List<CommandArgument> positional = new ArrayList<>();
        List<CommandArgument> options = new ArrayList<>();

        for (CommandArgument arg : arguments) {
            if (senderArgument.test(arg)) {
                continue;
            }
            if (arg.isOption()) {
                options.add(arg);
            } else {
                positional.add(arg);
            }
        }

        for (CommandArgument arg : positional) {
            usage.append(" ");

            boolean isOptional = forceOptional || arg.isOptional() || arg.getDefaultValue() != null;

            if (isOptional) {
                usage.append("[");
            } else {
                usage.append("<");
            }

            String argName = generateArgumentName(arg);
            usage.append(argName);

            if (isOptional) {
                usage.append("]");
            } else {
                usage.append(">");
            }
        }

        for (CommandArgument arg : options) {
            usage.append(" ");
            boolean isOptional = forceOptional || arg.isOptional() || arg.getDefaultValue() != null;
            String optionToken = buildPrimaryOptionToken(arg);
            String token = optionToken;
            if (!arg.isFlag()) {
                token += " <" + generateArgumentName(arg) + ">";
            }
            if (isOptional) {
                usage.append("[").append(token).append("]");
            } else {
                usage.append(token);
            }
        }
    }

    String buildUsage(CommandInfo info, @Nullable String subCommandName, List<CommandArgument> arguments) {
        StringBuilder usage = new StringBuilder("/").append(info.name());
        if (subCommandName != null && !subCommandName.isEmpty()) {
            usage.append(" ").append(subCommandName);
        }
        appendArgumentsToUsage(usage, arguments, false);
        return escapeMiniMessage(usage.toString());
    }

    private static String escapeMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
    }

    /**
     * Generates a descriptive name for an argument based on its type and
     * suggestions.
     * 
     * @param argument the command argument
     * @return descriptive argument name
     */
    private String generateArgumentName(CommandArgument argument) {
        String displayName = argument.getName();
        String typeName = argument.getType().getSimpleName().toLowerCase();

        // Check if argument has suggestions that can provide better naming
        for (String suggestion : argument.getSuggestions()) {
            if (suggestion.startsWith("{") && suggestion.endsWith("}")) {
                String content = suggestion.substring(1, suggestion.length() - 1);
                String[] options = content.split(",\\s*");
                if (options.length <= 3) {
                    // If few options, show them directly
                    return String.join("|", options);
                } else {
                    // If many options, show type with hint
                    return typeName + ":" + options[0] + "|...";
                }
            } else if (suggestion.equals("@players")) {
                return displayName != null ? displayName : "player";
            } else if (suggestion.equals("@worlds")) {
                return displayName != null ? displayName : "world";
            } else if (suggestion.equals("@sender")) {
                return displayName != null ? displayName : "player";
            }
        }

        if (argument.getType().isEnum()) {
            Object[] constants = argument.getType().getEnumConstants();
            if (constants != null && constants.length > 0) {
                List<String> variants = Arrays.stream(constants)
                        .map(String::valueOf)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .toList();
                if (variants.size() <= 4) {
                    return String.join("|", variants);
                }
                return (displayName != null ? displayName : typeName) + ":" + variants.get(0) + "|...";
            }
        }

        // Fallback to type-based naming
        switch (typeName) {
            case "player":
                return displayName != null ? displayName : "player";
            case "world":
                return displayName != null ? displayName : "world";
            case "string":
                return displayName != null ? displayName : "text";
            case "integer":
                return displayName != null ? displayName : "number";
            case "long":
                return displayName != null ? displayName : "number";
            case "boolean":
                return displayName != null ? displayName : "true|false";
            default:
                return displayName != null ? displayName : typeName;
        }
    }

    private String buildPrimaryOptionToken(CommandArgument argument) {
        if (argument == null) {
            return "";
        }
        List<String> longNames = argument.getLongOptionNames();
        if (!longNames.isEmpty()) {
            return "--" + longNames.get(0);
        }
        List<String> shortNames = argument.getShortOptionNames();
        if (!shortNames.isEmpty()) {
            return "-" + shortNames.get(0);
        }
        String name = argument.getName();
        return name != null ? name : "";
    }

    private List<String> getTopLevelSubCommandNames(List<CommandAction<S>> subCommands) {
        Set<String> names = new LinkedHashSet<>();
        if (subCommands == null) {
            return new ArrayList<>();
        }
        for (CommandAction<S> sub : subCommands) {
            if (sub == null) {
                continue;
            }
            List<String> path = sub.path();
            if (path != null && !path.isEmpty()) {
                String first = path.get(0);
                if (first != null && !first.isBlank()) {
                    names.add(first);
                    continue;
                }
            }
            String name = sub.name();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }
}
