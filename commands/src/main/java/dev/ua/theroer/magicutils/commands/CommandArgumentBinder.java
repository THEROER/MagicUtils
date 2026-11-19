package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.commands.CommandOptions.OptionIndex;
import dev.ua.theroer.magicutils.commands.CommandOptions.ParsedOptions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

import static dev.ua.theroer.magicutils.commands.CommandOptions.buildOptionIndex;
import static dev.ua.theroer.magicutils.commands.CommandOptions.parseOptions;

/**
 * Binds raw command tokens to the declared arguments of an action: sender injection,
 * option/flag values, type-aware positional matching, defaults and greedy tails.
 *
 * @param <S> sender type
 */
final class CommandArgumentBinder<S> {
    private final CommandPlatform<S> platform;
    private final CommandLogger logger;
    private final TypeParserRegistry<S> typeParserRegistry;
    private final CommandPermissions<S> permissions;
    private final Predicate<CommandArgument> senderArgument;

    CommandArgumentBinder(CommandPlatform<S> platform,
                          CommandLogger logger,
                          TypeParserRegistry<S> typeParserRegistry,
                          CommandPermissions<S> permissions,
                          Predicate<CommandArgument> senderArgument) {
        this.platform = platform;
        this.logger = logger;
        this.typeParserRegistry = typeParserRegistry;
        this.permissions = permissions;
        this.senderArgument = senderArgument;
    }

    Object[] parseArgumentsForDirectMethod(List<CommandArgument> arguments, List<String> args,
            S sender, String normalizedCommandName, @Nullable String subCommandName) {
        Object[] result = new Object[arguments.size()];
        boolean[] filled = new boolean[arguments.size()];
        OptionIndex optionIndex = buildOptionIndex(arguments, senderArgument);
        ParsedOptions parsedOptions = optionIndex.hasOptions()
                ? parseOptions(args, optionIndex, false)
                : new ParsedOptions(args != null ? new ArrayList<>(args) : new ArrayList<>(),
                        new HashMap<>(), new HashSet<>(), null, false);
        if (parsedOptions == null) {
            return null;
        }

        // First pass: auto-fill sender arguments
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (senderArgument.test(argument)) {
                try {
                    result[i] = platform.resolveSenderArgument(sender, argument);
                    filled[i] = true;
                    logger.debug("Auto-filled sender argument " + i + " (" + argument.getName()
                            + "): " + platform.getName(sender));
                } catch (SenderMismatchException ex) {
                    logger.debug("Sender mismatch for argument " + argument.getName() + ": "
                            + ex.getMessage());
                    throw ex;
                }
            }
        }

        // Option arguments (including flags)
        if (optionIndex.hasOptions()) {
            for (int i = 0; i < arguments.size(); i++) {
                if (filled[i]) {
                    continue;
                }
                CommandArgument argument = arguments.get(i);
                if (senderArgument.test(argument) || !argument.isOption()) {
                    continue;
                }
                if (argument.isFlag() && !parsedOptions.values().containsKey(argument)) {
                    String defaultValue = argument.getDefaultValue();
                    if (defaultValue == null) {
                        defaultValue = "false";
                    }
                    result[i] = convertArgument(defaultValue, argument.getType(), sender);
                    filled[i] = true;
                    continue;
                }
                String optionValue = parsedOptions.values().get(argument);
                if (optionValue == null) {
                    continue;
                }
                TypeParseResult<Object> parsedOption = convertArgumentDetailed(optionValue, argument.getType(), sender);
                result[i] = parsedOption.value();
                filled[i] = true;
                if (parsedOption.isInvalid()) {
                    logger.debug("Failed to convert option value for argument " + argument.getName()
                            + " from value: " + optionValue);
                    return null;
                }
            }
        }

        // Second pass: try to match user arguments intelligently
        List<String> remainingArgs = new ArrayList<>(parsedOptions.positionals());

        for (int i = 0; i < arguments.size(); i++) {
            if (filled[i])
                continue; // Skip already filled arguments

            CommandArgument argument = arguments.get(i);
            if (argument.isFlag()) {
                continue;
            }

            if (permissions.lacksArgumentPermission(normalizedCommandName, subCommandName, argument, sender)) {
                logger.debug("Skipping argument " + argument.getName() + " due to missing permission, setting to default/null");
                if (argument.getDefaultValue() != null) {
                    result[i] = convertArgument(argument.getDefaultValue(), argument.getType(), sender);
                } else {
                    result[i] = null;
                }
                filled[i] = true;
                if (!remainingArgs.isEmpty() && !senderArgument.test(argument) && !argument.isOption()) {
                    remainingArgs.remove(0);
                }
                continue;
            }

            if (argument.isGreedy()) {
                continue; // Greedy handled in final pass
            }
            String bestMatch = null;
            int bestMatchIndex = -1;

            // Try to find the best match for this parameter
            for (int j = 0; j < remainingArgs.size(); j++) {
                String userArg = remainingArgs.get(j);

                // Check if this user argument is a good match for this parameter
                if (isArgumentMatch(argument, userArg, sender)) {
                    bestMatch = userArg;
                    bestMatchIndex = j;
                    break; // Take first good match to maintain order priority
                }
            }

            // If we found a match, use it
            if (bestMatch != null) {
                result[i] = convertArgument(bestMatch, argument.getType(), sender);
                filled[i] = true;
                remainingArgs.remove(bestMatchIndex);
                logger.debug("Matched user arg '" + bestMatch + "' to parameter " + i + " (" + argument.getName() + ")");
            }
        }

        // Third pass: fill remaining arguments in order
        int userArgIndex = 0;
        for (int i = 0; i < arguments.size(); i++) {
            if (filled[i])
                continue; // Skip already filled arguments

            CommandArgument argument = arguments.get(i);
            String value;
            boolean providedByUser = false;

            if (argument.isFlag()) {
                String defaultValue = argument.getDefaultValue();
                if (defaultValue == null) {
                    defaultValue = "false";
                }
                result[i] = convertArgument(defaultValue, argument.getType(), sender);
                filled[i] = true;
                continue;
            }

            if (argument.isGreedy()) {
                // Consume all remaining args joined by space
                if (userArgIndex < remainingArgs.size()) {
                    value = String.join(" ", remainingArgs.subList(userArgIndex, remainingArgs.size()));
                    userArgIndex = remainingArgs.size();
                    providedByUser = true;
                    logger.debug("Greedy parameter " + i + " (" + argument.getName() + ") consumed remaining args: " + value);
                } else if (argument.getDefaultValue() != null) {
                    value = argument.getDefaultValue();
                    logger.debug("Used default value for greedy parameter " + i + " (" + argument.getName() + "): " + value);
                } else if (argument.isOptional()) {
                    value = null;
                    logger.debug("Used null for optional greedy parameter " + i + " (" + argument.getName() + ")");
                } else {
                    logger.debug("Missing required greedy argument at position " + i + ": " + argument.getName());
                    return null;
                }
            } else {
                if (userArgIndex < remainingArgs.size()) {
                    value = remainingArgs.get(userArgIndex);
                    userArgIndex++;
                    providedByUser = true;
                    logger.debug("Filled remaining parameter " + i + " (" + argument.getName() + ") with user arg: " + value);
                } else if (argument.getDefaultValue() != null) {
                    value = argument.getDefaultValue();
                    logger.debug("Used default value for parameter " + i + " (" + argument.getName() + "): " + value);
                } else if (argument.isOptional()) {
                    value = null;
                    logger.debug("Used null for optional parameter " + i + " (" + argument.getName() + ")");
                } else {
                    logger.debug("Missing required argument at position " + i + ": " + argument.getName());
                    return null;
                }
            }

            TypeParseResult<Object> parsedArgument = convertArgumentDetailed(value, argument.getType(), sender);
            result[i] = parsedArgument.value();
            logger.debug("Parsed argument " + i + " (" + argument.getName() + "): " + result[i]
                    + " (type: " + (result[i] != null ? result[i].getClass().getSimpleName() : "null") + ")");

            if (providedByUser && parsedArgument.isInvalid()) {
                if (argument.isOptional() && canLaterArgumentConsume(arguments, i + 1, value, sender)) {
                    userArgIndex = Math.max(0, userArgIndex - 1);
                    logger.debug("Conversion failed for optional argument " + argument.getName()
                            + ", reusing value for next parameter");
                    continue;
                }
                logger.debug("Failed to convert argument " + i + " (" + argument.getName()
                        + ") from value: " + value + " to type: " + argument.getType().getSimpleName());
                return null;
            }

            // Check if conversion failed
            if (value != null && parsedArgument.isInvalid() && !argument.isOptional()) {
                logger.debug("Failed to convert argument " + i + " (" + argument.getName()
                        + ") from value: " + value + " to type: " + argument.getType().getSimpleName());
                return null;
            }

            // A default the author wrote (@DefaultValue) is a promise that the parameter
            // always has a value, so a default that fails to resolve is an error, not a
            // silent null: @DefaultValue("@sender") resolves to no player from console,
            // and passing null would NPE inside the command instead of telling the user
            // to name a player. @Nullable / @OptionalArgument opt out by declaring that
            // null is expected, so they keep the old behaviour.
            if (!providedByUser && parsedArgument.isInvalid() && !argument.isNullable()) {
                logger.debug("Default value did not resolve for argument " + i + " ("
                        + argument.getName() + "): " + value);
                return null;
            }
        }

        return result;
    }

    private boolean isArgumentMatch(CommandArgument argument, String userArg, S sender) {
        if (argument.isGreedy()) {
            return false;
        }

        // Check if the argument type can successfully parse this value
        Object converted = convertArgument(userArg, argument.getType(), sender);

        // If conversion succeeded and didn't just fall back to string
        if (converted != null && !converted.equals(userArg)) {
            return true;
        }

        // Check if this value is in the argument's suggestions
        if (argumentHasSuggestion(argument, userArg, sender)) {
            return true;
        }

        // For string arguments, accept anything as last resort
        if (argument.getType().equals(String.class)) {
            return true;
        }

        return false;
    }

    private boolean argumentHasSuggestion(CommandArgument argument, String value, S sender) {
        for (String suggestionSource : argument.getSuggestions()) {
            // Use the suggestion parser to get all possible values
            List<String> suggestions = typeParserRegistry.parseSuggestion(suggestionSource, sender);
            for (String suggestion : suggestions) {
                if (suggestion.equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canLaterArgumentConsume(List<CommandArgument> arguments, int startIndex, String value, S sender) {
        if (value == null) {
            return false;
        }
        for (int i = startIndex; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (argument == null || senderArgument.test(argument) || argument.isFlag()) {
                continue;
            }
            if (argument.isGreedy()) {
                if (argument.getType().equals(String.class)) {
                    return true;
                }
                TypeParseResult<Object> parsed = convertArgumentDetailed(value, argument.getType(), sender);
                return parsed.isSuccess() || argumentHasSuggestion(argument, value, sender);
            }
            if (isArgumentMatch(argument, value, sender)) {
                return true;
            }
            if (!argument.isOptional() && argument.getDefaultValue() == null) {
                return false;
            }
        }
        return false;
    }

    Object convertArgument(String value, Class<?> type, S sender) {
        return convertArgumentDetailed(value, type, sender).value();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TypeParseResult<Object> convertArgumentDetailed(String value, Class<?> type, S sender) {
        logger.debug("Converting argument: '" + value + "' to type: " + type.getSimpleName());
        return (TypeParseResult<Object>) typeParserRegistry.parseDetailed(value, (Class) type, sender);
    }
}
