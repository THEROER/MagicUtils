package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;
import dev.ua.theroer.magicutils.commands.CommandOptions.OptionIndex;
import dev.ua.theroer.magicutils.commands.CommandOptions.ParsedOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.ua.theroer.magicutils.commands.CommandOptions.buildOptionIndex;
import static dev.ua.theroer.magicutils.commands.CommandOptions.isOptionPrefix;
import static dev.ua.theroer.magicutils.commands.CommandOptions.parseOptions;

/**
 * Tab-completion engine: walks the subcommand tree, honours permissions, and asks
 * type parsers, literal lists or provider methods for argument suggestions.
 *
 * @param <S> sender type
 */
final class CommandSuggestionEngine<S> {
    private final CommandManager<S> manager;
    private final CommandPlatform<S> platform;
    private final CommandLogger logger;
    private final TypeParserRegistry<S> typeParserRegistry;
    private final CommandPermissions<S> permissions;
    private final CommandDescriptionResolver descriptions;
    private final CommandArgumentBinder<S> argumentBinder;
    private final Predicate<CommandArgument> senderArgument;

    CommandSuggestionEngine(CommandManager<S> manager,
                            CommandPlatform<S> platform,
                            CommandLogger logger,
                            TypeParserRegistry<S> typeParserRegistry,
                            CommandPermissions<S> permissions,
                            CommandDescriptionResolver descriptions,
                            CommandArgumentBinder<S> argumentBinder,
                            Predicate<CommandArgument> senderArgument) {
        this.manager = manager;
        this.platform = platform;
        this.logger = logger;
        this.typeParserRegistry = typeParserRegistry;
        this.permissions = permissions;
        this.descriptions = descriptions;
        this.argumentBinder = argumentBinder;
        this.senderArgument = senderArgument;
    }

    List<String> generateSuggestions(MagicCommand command, CommandInfo info, S sender,
            List<String> args, String normalizedCommandName) {
        List<CommandAction<S>> subCommands = manager.getSubCommandActions(command);
        CommandAction<S> directAction = manager.getDirectAction(command, info);

        logger.debug(() -> "Generating suggestions - executeHandler: " + (directAction != null)
                + ", subCommands: " + subCommands.size() + ", args: " + args);

        // If there's only a direct execute handler and no subcommands
        if (directAction != null && subCommands.isEmpty()) {
            List<CommandArgument> arguments = directAction.arguments();
            logger.debug(() -> "Using direct method suggestions with " + arguments.size() + " arguments");
            return generateDirectMethodSuggestions(command, arguments, sender, args, normalizedCommandName, null, Collections.emptyMap());
        }

        // If there are no subcommands and no execute handler
        if (subCommands.isEmpty() && directAction == null) {
            logger.debug(() -> "No subcommands and no execute handler");
            return Collections.emptyList();
        }

        SubCommandTree.Node<S> root = SubCommandTree.build(subCommands);

        // If no args and we have subcommands, show top-level subcommands
        if (args.isEmpty() && !subCommands.isEmpty()) {
            logger.debug("No args, showing subcommands");
            return permissions.getAvailableChildNames(root, sender, normalizedCommandName);
        }

        String currentInput = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> fixedTokens = args.size() > 1 ? args.subList(0, args.size() - 1) : List.of();

        SubCommandTree.Traversal<S> traversal = SubCommandTree.traverse(root, fixedTokens);
        if (traversal.consumed() < fixedTokens.size() && traversal.lastActionNode() == null) {
            return Collections.emptyList();
        }

        SubCommandTree.Node<S> node = traversal.node();
        CommandAction<S> matchedAction = traversal.lastActionNode() != null ? traversal.lastActionNode().action() : null;
        int actionIndex = traversal.lastActionIndex();

        if (matchedAction != null && actionIndex < fixedTokens.size()) {
            String subPermission = permissions.resolvePermission(matchedAction.permission(),
                    "commands." + normalizedCommandName + ".subcommand." + matchedAction.permissionSegment());
            platform.ensurePermissionRegistered(subPermission, matchedAction.permissionDefault(),
                    descriptions.resolveActionDescription(normalizedCommandName, matchedAction));
            if (!subPermission.isEmpty()
                    && !platform.hasPermission(sender, subPermission, matchedAction.permissionDefault())) {
                return Collections.emptyList();
            }
            List<String> subArgs = args.subList(actionIndex, args.size());
            return generateArgumentSuggestions(command, matchedAction, sender, subArgs, currentInput,
                    normalizedCommandName, Collections.emptyMap()); // Initial call, no previous parsed args yet
        }

        List<String> availableChildren = permissions.getAvailableChildNames(node, sender, normalizedCommandName);
        List<String> filteredChildren = availableChildren.stream()
                .filter(sub -> sub.toLowerCase(Locale.ROOT).startsWith(currentInput.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        boolean matchesChild = availableChildren.stream()
                .anyMatch(sub -> sub.equalsIgnoreCase(currentInput));

        if (matchedAction == null) {
            if (directAction != null && fixedTokens.isEmpty() && !matchesChild) {
                List<CommandArgument> arguments = directAction.arguments();
                List<String> directSuggestions = generateDirectMethodSuggestions(command, arguments, sender, args,
                        normalizedCommandName, null, new HashMap<>()); // Pass empty map here
                List<String> combined = new ArrayList<>(directSuggestions);
                combined.addAll(filteredChildren);
                return combined;
            }
            return filteredChildren;
        }

        String subPermission = permissions.resolvePermission(matchedAction.permission(),
                "commands." + normalizedCommandName + ".subcommand." + matchedAction.permissionSegment());
        platform.ensurePermissionRegistered(subPermission, matchedAction.permissionDefault(),
                descriptions.resolveActionDescription(normalizedCommandName, matchedAction));
        if (!subPermission.isEmpty()
                && !platform.hasPermission(sender, subPermission, matchedAction.permissionDefault())) {
            return filteredChildren.isEmpty() ? Collections.emptyList() : filteredChildren;
        }

        List<String> subArgs = args.subList(actionIndex, args.size());
        List<String> argumentSuggestions = generateArgumentSuggestions(command, matchedAction, sender, subArgs,
                currentInput, normalizedCommandName, Collections.emptyMap()); // Initial call, no previous parsed args yet

        if (!filteredChildren.isEmpty()) {
            if (!matchesChild) {
                List<String> combined = new ArrayList<>(argumentSuggestions);
                combined.addAll(filteredChildren);
                return combined;
            }
            return filteredChildren;
        }

        return argumentSuggestions;
    }

    private List<String> generateDirectMethodSuggestions(MagicCommand command, List<CommandArgument> arguments,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> previousParsedArguments) {
        OptionIndex optionIndex = buildOptionIndex(arguments, senderArgument);
        if (optionIndex.hasOptions()) {
            return generateSuggestionsWithOptions(command, arguments, optionIndex, sender, args,
                    normalizedCommandName, subCommandName, previousParsedArguments);
        }
        return generatePositionalSuggestions(command, arguments, sender, args, normalizedCommandName, subCommandName,
                previousParsedArguments);
    }

    private List<String> generatePositionalSuggestions(MagicCommand command, List<CommandArgument> arguments,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> allParsedArguments) {
        return generatePositionalSuggestions(command, arguments, sender, args, normalizedCommandName, subCommandName,
                allParsedArguments, Collections.emptySet());
    }

    private List<String> generatePositionalSuggestions(MagicCommand command, List<CommandArgument> arguments,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> allParsedArguments, @NotNull Set<CommandArgument> skipArguments) {
        logger.debug(() -> "generatePositionalSuggestions called with " + arguments.size()
                + " arguments and " + args.size() + " args");
        logger.debug(() -> "Raw args: " + args);

        if (arguments.isEmpty()) {
            logger.debug(() -> "No arguments defined for direct method");
            return Collections.emptyList();
        }

        // Build a list of arguments that need user input (skip sender-bound parameters)
        allParsedArguments = new HashMap<>(allParsedArguments);
        List<ArgumentInfo> userInputArguments = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            if (skipArguments.contains(arg)) {
                continue;
            }
            // Skip explicit sender parameters, they are handled by platform.resolveSenderArgument and not user input
            if (senderArgument.test(arg)) {
                try {
                    allParsedArguments.put(arg.getName(), platform.resolveSenderArgument(sender, arg));
                } catch (SenderMismatchException ex) {
                    // Ignore, this should not prevent suggestions and will be caught during execution
                }
            } else if (arg.isFlag()) {
                // Flags are options, not positional arguments, handled elsewhere
                continue;
            } else {
                userInputArguments.add(new ArgumentInfo(arg));
            }
        }

        int currentArgPosition = 0;
        for (int i = 0; i < args.size() - 1; i++) {
            if (currentArgPosition >= userInputArguments.size()) {
                break;
            }

            ArgumentInfo argInfo = userInputArguments.get(currentArgPosition);
            String token = args.get(i);
            Object parsedValue = argumentBinder.convertArgument(token, argInfo.argument.getType(), sender);

            if (parsedValue != null) {
                allParsedArguments.put(argInfo.argument.getName(), parsedValue);
                currentArgPosition++;
            } else if (argInfo.argument.isOptional()) {
                allParsedArguments.put(argInfo.argument.getName(), null);
                currentArgPosition++;
                i--; 
            } else {
                break;
            }
        }

        List<String> suggestions = new ArrayList<>();

        if (args.isEmpty()) {
            if (!userInputArguments.isEmpty()) {
                ArgumentInfo firstArg = userInputArguments.get(0);
                if (permissions.canSuggestArgument(normalizedCommandName, subCommandName, firstArg.argument, sender)) {
                    suggestions.addAll(generateSuggestionsForArgument(command, firstArg.argument, sender, "",
                            allParsedArguments));
                }
            }
            return suggestions.stream().distinct().collect(Collectors.toList());
        }

        String currentInput = args.get(args.size() - 1);
        int targetArgumentIndex = currentArgPosition;

        if (targetArgumentIndex < userInputArguments.size()) {
            ArgumentInfo targetArg = userInputArguments.get(targetArgumentIndex);
            if (permissions.canSuggestArgument(normalizedCommandName, subCommandName, targetArg.argument, sender)) {
                suggestions.addAll(generateSuggestionsForArgument(command, targetArg.argument, sender, currentInput,
                        allParsedArguments));
            }
        }

        if (targetArgumentIndex < userInputArguments.size()) {
            ArgumentInfo targetArg = userInputArguments.get(targetArgumentIndex);
            if ((targetArg.argument.isOptional() || targetArg.argument.getDefaultValue() != null)
                    && targetArgumentIndex + 1 < userInputArguments.size()) {
                ArgumentInfo nextArg = userInputArguments.get(targetArgumentIndex + 1);
                if (permissions.canSuggestArgument(normalizedCommandName, subCommandName, nextArg.argument, sender)) {
                    suggestions.addAll(generateSuggestionsForArgument(command, nextArg.argument, sender, "",
                            allParsedArguments));
                }
            }
        }

        return suggestions.stream().distinct().collect(Collectors.toList());
    }

    private List<String> generateSuggestionsWithOptions(MagicCommand command, List<CommandArgument> arguments,
            OptionIndex optionIndex, S sender, List<String> args,
            String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> allParsedArguments) {
        if (arguments.isEmpty()) {
            return Collections.emptyList();
        }
        String currentInput = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> priorTokens = args.size() > 1 ? args.subList(0, args.size() - 1) : List.of();
        ParsedOptions parsed = parseOptions(priorTokens, optionIndex, true);
        if (parsed == null) {
            return Collections.emptyList();
        }

        for (Map.Entry<CommandArgument, String> entry : parsed.values().entrySet()) {
            CommandArgument arg = entry.getKey();
            String value = entry.getValue();
            Object converted = argumentBinder.convertArgument(value, arg.getType(), sender);
            if (converted != null) {
                allParsedArguments.put(arg.getName(), converted);
            }
        }
        
        CommandArgument pending = parsed.pendingValue();
        if (pending != null) {
            if (!permissions.canSuggestArgument(normalizedCommandName, subCommandName, pending, sender)) {
                return Collections.emptyList();
            }
            return generateSuggestionsForArgument(command, pending, sender, currentInput, allParsedArguments);
        }

        boolean optionsTerminated = parsed.optionsTerminated();
        boolean currentIsOption = !optionsTerminated && isOptionPrefix(currentInput);
        List<String> suggestions = new ArrayList<>();
        Set<CommandArgument> providedByOptions = parsed.usedOptions() != null
                ? new HashSet<>(parsed.usedOptions())
                : Collections.emptySet();

        if (!currentIsOption) {
            List<String> positional = new ArrayList<>(parsed.positionals());
            positional.add(currentInput);
            suggestions.addAll(generatePositionalSuggestions(command, arguments, sender, positional,
                    normalizedCommandName, subCommandName, allParsedArguments, providedByOptions));
        }

        if (!optionsTerminated && (currentInput.isEmpty() || currentIsOption)) {
            suggestions.addAll(buildOptionSuggestions(optionIndex, parsed.usedOptions(), currentInput,
                    normalizedCommandName, subCommandName, sender, allParsedArguments));
        }

        return suggestions.stream().distinct().collect(Collectors.toList());
    }

    private static class ArgumentInfo {
        final CommandArgument argument;

        ArgumentInfo(CommandArgument argument) {
            this.argument = argument;
        }
    }

    private List<String> buildOptionSuggestions(OptionIndex optionIndex, Set<CommandArgument> usedOptions,
            String currentInput, String normalizedCommandName, @Nullable String subCommandName, S sender,
            @NotNull Map<String, Object> allParsedArguments) {
        if (optionIndex == null || !optionIndex.hasOptions()) {
            return List.of();
        }
        String lowered = currentInput != null ? currentInput.toLowerCase(Locale.ROOT) : "";
        List<String> suggestions = new ArrayList<>();

        for (CommandArgument argument : optionIndex.optionArguments()) {
            if (argument == null) {
                continue;
            }
            if (usedOptions != null && usedOptions.contains(argument)) {
                continue;
            }
            if (!permissions.canSuggestArgument(normalizedCommandName, subCommandName, argument, sender)) {
                continue;
            }
            for (String longName : argument.getLongOptionNames()) {
                String token = "--" + longName;
                if (lowered.isEmpty() || token.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                    suggestions.add(token);
                }
            }
            for (String shortName : argument.getShortOptionNames()) {
                String token = "-" + shortName;
                if (lowered.isEmpty() || token.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                    suggestions.add(token);
                }
            }
        }

        return suggestions;
    }

    private List<String> generateArgumentSuggestions(MagicCommand command, CommandAction<S> subInfo,
            S sender, List<String> args, String currentInput, String normalizedCommandName,
            Map<String, Object> previousParsedArguments) {
        List<CommandArgument> arguments = subInfo != null ? subInfo.arguments() : List.of();
        return generateDirectMethodSuggestions(command, arguments, sender, args, normalizedCommandName,
                subInfo != null ? subInfo.fullPath() : null, previousParsedArguments);
    }

    private List<String> generateSuggestionsForArgument(MagicCommand command, CommandArgument argument,
            S sender, String currentInput, Map<String, Object> previousParsedArguments) {
        logger.debug(() -> "generateSuggestionsForArgument called for argument: " + argument.getName()
                + " with input: '" + currentInput + "'");
        logger.debug(() -> "Argument suggestions: " + argument.getSuggestions());
        logger.debug(() -> "Argument type: " + argument.getType());

        List<String> suggestions = new ArrayList<>();

        // If no explicit suggestions, try to get suggestions from the argument type
        if (argument.getSuggestions().isEmpty()) {
            logger.debug(() -> "No explicit suggestions, getting suggestions for type: "
                    + argument.getType().getSimpleName());
            List<String> typeSuggestions = typeParserRegistry.getSuggestionsForArgumentFiltered(argument,
                    currentInput, sender, previousParsedArguments);
            if (!typeSuggestions.isEmpty()) {
                logger.debug(() -> "Got " + typeSuggestions.size() + " suggestions from type parser");
                return typeSuggestions;
            }
        }

        // Process explicit suggestions
        for (String suggestionSource : argument.getSuggestions()) {
            logger.debug(() -> "Processing suggestion source: '" + suggestionSource + "'");

            if (suggestionSource.contains("|")) {
                String[] sources = suggestionSource.split("\\|");
                for (String source : sources) {
                    List<String> sourceSuggestions = processSuggestionSource(command, source.trim(), sender,
                            currentInput, argument, previousParsedArguments);
                    logger.debug(() -> "Source '" + source.trim() + "' generated: " + sourceSuggestions);
                    suggestions.addAll(sourceSuggestions);
                }
            } else {
                List<String> sourceSuggestions = processSuggestionSource(command, suggestionSource, sender,
                        currentInput, argument, previousParsedArguments);
                logger.debug(() -> "Source '" + suggestionSource + "' generated: " + sourceSuggestions);
                suggestions.addAll(sourceSuggestions);
            }
        }

        List<String> filteredSuggestions = suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentInput.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        logger.debug(() -> "Final filtered suggestions: " + filteredSuggestions);
        return filteredSuggestions;
    }

    @SuppressWarnings("unchecked")
    private List<String> processSuggestionSource(MagicCommand command, String source, S sender,
            String currentInput, CommandArgument currentArgument, Map<String, Object> previousParsedArguments) {
        logger.debug("Processing suggestion source: " + source);

        if ("@commands".equalsIgnoreCase(source)) {
            return Arrays.asList(HelpCommandSupport.getCommandSuggestions(manager));
        }


        if (typeParserRegistry.isSpecialSuggestion(source)) {
            return typeParserRegistry.parseSuggestionFiltered(source, currentInput, sender);
        }

        if (source.startsWith("{") && source.endsWith("}")) {
            String content = source.substring(1, source.length() - 1);
            List<String> values = new ArrayList<>();
            for (String part : content.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }

        List<Object> dynamicArgs = new ArrayList<>();
        List<Class<?>> dynamicArgTypes = new ArrayList<>();

        Class<?> playerType = platform.playerType();
        Class<?> senderType = platform.senderType();

        boolean methodTakesPlayer = false;

        // Provider methods resolve against the argument's suggestion host when set
        // (the original carrier instance a flat-mounted/merged command came from),
        // else the registered command itself. Without this, a source naming a
        // carrier method is invisible after mount because the registered command is
        // a wrapper that does not declare it — the method would then be misread as a
        // literal below.
        Object provider = currentArgument.getSuggestionHost() != null
                ? currentArgument.getSuggestionHost()
                : command;

        // Whether a provider method with this name exists at all (any signature).
        // If none does and no special form matched, the source is a static literal
        // value (e.g. @Suggest({"void", "normal", "flat"}) — each array element is a
        // ready-made completion, not a method name), so it is returned verbatim
        // below. A real method-name typo therefore still yields nothing (the method
        // is absent) — but so would an intended literal; we prefer the literal since
        // an existing-method name always wins here.
        boolean providerMethodExists = hasMethodNamed(provider.getClass(), source);

        // A no-arg provider is the common case, but only when the declaration asks for
        // nothing: with contextArgs the author wants those values, so a same-named
        // no-arg overload must not shadow the context-aware one below (it would drop
        // the context silently and suggest the wrong list).
        if (currentArgument.getContextArgs().isEmpty()) {
            try {
                Method method = provider.getClass().getMethod(source);
                Object result = method.invoke(provider);
                if (result instanceof String[]) {
                    return Arrays.asList((String[]) result);
                } else if (result instanceof List) {
                    return (List<String>) result;
                }
            } catch (Exception e) {
            }
        }

        if (currentArgument.getContextArgs().isEmpty()) {
            if (playerType != null) {
                Object player = platform.getPlayerSender(sender);
                if (player != null) {
                    dynamicArgs.add(player);
                    dynamicArgTypes.add(playerType);
                    methodTakesPlayer = true;
                }
            }
            if (senderType != null && !methodTakesPlayer) {
                dynamicArgs.add(sender);
                dynamicArgTypes.add(senderType);
            }
        }

        for (String contextArgName : currentArgument.getContextArgs()) {
            Object argValue = previousParsedArguments.get(contextArgName);
            if (argValue != null) {
                dynamicArgs.add(argValue);
                dynamicArgTypes.add(argValue.getClass());
            } else {
                logger.debug("Missing context argument '" + contextArgName + "' for suggestion method " + source);
                return Collections.emptyList();
            }
        }
        
        // Sender and the partially-typed word are both conveniences a provider may or
        // may not declare, so try the candidate signatures from richest to plainest
        // and call the first one that exists. Notably currentInput is offered even
        // when empty — tab on a bare argument is the common case, and a provider that
        // declares it should not need a second overload just to serve it.
        int senderArgs = dynamicArgs.size() - currentArgument.getContextArgs().size();
        List<Object> withoutSender = dynamicArgs.subList(senderArgs, dynamicArgs.size());
        List<Class<?>> withoutSenderTypes = dynamicArgTypes.subList(senderArgs, dynamicArgTypes.size());
        String input = currentInput != null ? currentInput : "";

        List<List<Object>> candidates = List.of(
                append(dynamicArgs, input),      // (sender?, context..., currentInput)
                dynamicArgs,                     // (sender?, context...)
                append(withoutSender, input),    // (context..., currentInput)
                withoutSender);                  // (context...)
        List<List<Class<?>>> candidateTypes = List.of(
                append(dynamicArgTypes, String.class),
                dynamicArgTypes,
                append(withoutSenderTypes, String.class),
                withoutSenderTypes);

        try {
            for (int i = 0; i < candidates.size(); i++) {
                Method matchingMethod = findMatchingMethod(provider.getClass(), source, candidateTypes.get(i));
                if (matchingMethod == null) {
                    continue;
                }
                Object result = matchingMethod.invoke(provider, candidates.get(i).toArray());
                if (result instanceof String[]) {
                    return Arrays.asList((String[]) result);
                } else if (result instanceof List) {
                    return (List<String>) result;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to call suggestion method " + source + " with dynamic parameters: " + e.getMessage());
        }

        if ("@sender".equalsIgnoreCase(source)) {
            return Collections.emptyList();
        }

        // No special form and no provider method by this name: treat the source as a
        // literal completion value. This makes the intuitive array form
        // @Suggest({"a", "b", "c"}) work the same as @Suggest("{a, b, c}").
        if (!providerMethodExists) {
            String literal = source.trim();
            return literal.isEmpty() ? Collections.emptyList() : List.of(literal);
        }

        return Collections.emptyList();
    }

    /** @return {@code list} plus {@code extra}, as a new list. */
    private static <T> List<T> append(List<T> list, T extra) {
        List<T> out = new ArrayList<>(list);
        out.add(extra);
        return out;
    }

    /** @return true if {@code clazz} declares any accessible method named {@code name}. */
    private static boolean hasMethodNamed(Class<?> clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private Method findMatchingMethod(Class<?> clazz, String methodName, List<Class<?>> paramTypes) {
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] methodParamTypes = method.getParameterTypes();
            if (methodParamTypes.length != paramTypes.size()) {
                continue;
            }
            boolean typesMatch = true;
            for (int i = 0; i < paramTypes.size(); i++) {
                Class<?> expected = paramTypes.get(i);
                Class<?> actual = methodParamTypes[i];
                if (!actual.isAssignableFrom(expected) && !isPrimitiveWrapperOf(actual, expected)) {
                    typesMatch = false;
                    break;
                }
            }
            if (typesMatch) {
                return method;
            }
        }
        return null;
    }

    private boolean isPrimitiveWrapperOf(Class<?> wrapper, Class<?> primitive) {
        if (!wrapper.isPrimitive() && primitive.isPrimitive()) {
            if (wrapper == Integer.class && primitive == int.class) return true;
            if (wrapper == Long.class && primitive == long.class) return true;
            if (wrapper == Short.class && primitive == short.class) return true;
            if (wrapper == Byte.class && primitive == byte.class) return true;
            if (wrapper == Character.class && primitive == char.class) return true;
            if (wrapper == Float.class && primitive == float.class) return true;
            if (wrapper == Double.class && primitive == double.class) return true;
            if (wrapper == Boolean.class && primitive == boolean.class) return true;
        }
        return false;
    }
}
