package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.commands.HelpStyling.HelpStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.ua.theroer.magicutils.commands.HelpStyling.closeTag;
import static dev.ua.theroer.magicutils.commands.HelpStyling.escapeMiniText;
import static dev.ua.theroer.magicutils.commands.HelpStyling.openTag;

/**
 * Renders the pieces of help output that describe arguments: the label of a positional
 * argument or option, the enum values it accepts, and the optional-segment highlighting
 * of a usage line.
 */
final class HelpArgumentLabels {

    private HelpArgumentLabels() {
    }

    static String styleUsage(String usage, HelpStyle style) {
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

    static String argumentLabel(CommandArgument argument) {
        if (argument == null) {
            return "";
        }
        String valueLabel = argumentValueLabel(argument);
        if (argument.isOption()) {
            String optionNames = optionNamesLabel(argument);
            if (argument.isFlag()) {
                return optionNames;
            }
            return optionNames + " <" + valueLabel + ">";
        }
        return valueLabel;
    }

    private static String argumentValueLabel(CommandArgument argument) {
        String name = argument.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return argument.getType().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private static String optionNamesLabel(CommandArgument argument) {
        List<String> tokens = new ArrayList<>();
        for (String longName : argument.getLongOptionNames()) {
            tokens.add("--" + longName);
        }
        for (String shortName : argument.getShortOptionNames()) {
            tokens.add("-" + shortName);
        }
        if (tokens.isEmpty()) {
            String name = argument.getName();
            return name != null ? name : "";
        }
        return String.join(", ", tokens);
    }

    static List<String> enumValues(CommandArgument argument) {
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

    static String joinValues(List<String> values) {
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
}
