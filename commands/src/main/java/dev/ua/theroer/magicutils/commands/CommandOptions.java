package dev.ua.theroer.magicutils.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Option/flag tokenization for command arguments ({@code -f}, {@code --flag},
 * {@code --key=value}, {@code --} terminator). Stateless; shared by argument
 * binding and tab completion.
 */
final class CommandOptions {

    private CommandOptions() {
    }

    static final class OptionIndex {
        private final Map<String, CommandArgument> shortOptions;
        private final Map<String, CommandArgument> longOptions;
        private final List<CommandArgument> optionArguments;

        private OptionIndex(Map<String, CommandArgument> shortOptions,
                            Map<String, CommandArgument> longOptions,
                            List<CommandArgument> optionArguments) {
            this.shortOptions = shortOptions;
            this.longOptions = longOptions;
            this.optionArguments = optionArguments;
        }

        boolean hasOptions() {
            return !optionArguments.isEmpty();
        }

        List<CommandArgument> optionArguments() {
            return optionArguments;
        }

        CommandArgument resolve(OptionToken token) {
            if (token == null) {
                return null;
            }
            String key = token.keyLower();
            if (token.longForm()) {
                return longOptions.get(key);
            }
            return shortOptions.get(key);
        }
    }

    record OptionToken(String key, boolean longForm, String value, boolean hasInlineValue) {
        String keyLower() {
            return key != null ? key.toLowerCase(Locale.ROOT) : "";
        }
    }

    static final class ParsedOptions {
        private final List<String> positionals;
        private final Map<CommandArgument, String> values;
        private final Set<CommandArgument> usedOptions;
        private final CommandArgument pendingValue;
        private final boolean optionsTerminated;

        ParsedOptions(List<String> positionals,
                      Map<CommandArgument, String> values,
                      Set<CommandArgument> usedOptions,
                      CommandArgument pendingValue,
                      boolean optionsTerminated) {
            this.positionals = positionals;
            this.values = values;
            this.usedOptions = usedOptions;
            this.pendingValue = pendingValue;
            this.optionsTerminated = optionsTerminated;
        }

        List<String> positionals() {
            return positionals;
        }

        Map<CommandArgument, String> values() {
            return values;
        }

        Set<CommandArgument> usedOptions() {
            return usedOptions;
        }

        CommandArgument pendingValue() {
            return pendingValue;
        }

        boolean optionsTerminated() {
            return optionsTerminated;
        }
    }

    static OptionIndex buildOptionIndex(List<CommandArgument> arguments, Predicate<CommandArgument> isSenderArgument) {
        if (arguments == null || arguments.isEmpty()) {
            return new OptionIndex(new HashMap<>(), new HashMap<>(), List.of());
        }
        Map<String, CommandArgument> shortOptions = new HashMap<>();
        Map<String, CommandArgument> longOptions = new HashMap<>();
        List<CommandArgument> optionArgs = new ArrayList<>();

        for (CommandArgument argument : arguments) {
            if (argument == null || isSenderArgument.test(argument) || !argument.isOption()) {
                continue;
            }
            optionArgs.add(argument);
            for (String name : argument.getShortOptionNames()) {
                String normalized = normalizeOptionName(name);
                if (!normalized.isEmpty()) {
                    shortOptions.put(normalized.toLowerCase(Locale.ROOT), argument);
                }
            }
            for (String name : argument.getLongOptionNames()) {
                String normalized = normalizeOptionName(name);
                if (!normalized.isEmpty()) {
                    longOptions.put(normalized.toLowerCase(Locale.ROOT), argument);
                }
            }
        }

        return new OptionIndex(shortOptions, longOptions, optionArgs);
    }

    static ParsedOptions parseOptions(List<String> args, OptionIndex index, boolean allowPending) {
        List<String> positionals = new ArrayList<>();
        Map<CommandArgument, String> values = new HashMap<>();
        Set<CommandArgument> used = new HashSet<>();
        CommandArgument pending = null;
        if (args == null || args.isEmpty() || index == null || !index.hasOptions()) {
            return new ParsedOptions(args != null ? new ArrayList<>(args) : new ArrayList<>(), values, used, pending,
                    false);
        }
        boolean endOptions = false;
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (pending != null) {
                values.put(pending, token);
                used.add(pending);
                pending = null;
                continue;
            }
            if (!endOptions && "--".equals(token)) {
                endOptions = true;
                continue;
            }
            if (!endOptions) {
                OptionToken optionToken = parseOptionToken(token);
                if (optionToken != null) {
                    CommandArgument argument = index.resolve(optionToken);
                    if (argument != null) {
                        used.add(argument);
                        if (optionToken.hasInlineValue()) {
                            values.put(argument, optionToken.value());
                        } else if (argument.isFlag()) {
                            values.put(argument, "true");
                        } else if (i + 1 < args.size()) {
                            values.put(argument, args.get(++i));
                        } else if (allowPending) {
                            pending = argument;
                        } else {
                            return null;
                        }
                        continue;
                    }
                }
            }
            positionals.add(token);
        }
        if (!allowPending && pending != null) {
            return null;
        }
        return new ParsedOptions(positionals, values, used, pending, endOptions);
    }

    static OptionToken parseOptionToken(String token) {
        if (token == null || token.length() < 2 || !token.startsWith("-") || "-".equals(token)) {
            return null;
        }
        if (token.startsWith("--")) {
            String keyValue = token.substring(2);
            if (keyValue.isEmpty() || isNumericOptionToken(keyValue)) {
                return null;
            }
            int eq = keyValue.indexOf('=');
            String key = eq >= 0 ? keyValue.substring(0, eq) : keyValue;
            if (key.isEmpty()) {
                return null;
            }
            String value = eq >= 0 ? keyValue.substring(eq + 1) : null;
            return new OptionToken(key, true, value, eq >= 0);
        }

        String keyValue = token.substring(1);
        if (keyValue.isEmpty() || isNumericOptionToken(keyValue)) {
            return null;
        }
        int eq = keyValue.indexOf('=');
        String key = eq >= 0 ? keyValue.substring(0, eq) : keyValue;
        if (key.isEmpty()) {
            return null;
        }
        String value = eq >= 0 ? keyValue.substring(eq + 1) : null;
        return new OptionToken(key, false, value, eq >= 0);
    }

    static boolean isOptionPrefix(String token) {
        if (token == null || token.isEmpty() || !token.startsWith("-")) {
            return false;
        }
        if ("-".equals(token) || "--".equals(token)) {
            return true;
        }
        String raw = token.startsWith("--") ? token.substring(2) : token.substring(1);
        if (raw.isEmpty()) {
            return true;
        }
        return !isNumericOptionToken(raw);
    }

    static boolean isNumericOptionToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return Character.isDigit(first) || first == '.';
    }

    static String normalizeOptionName(String name) {
        String trimmed = name != null ? name.trim() : "";
        while (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }
}
