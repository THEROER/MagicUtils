package dev.ua.theroer.magicutils.commands.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandArgument;
import dev.ua.theroer.magicutils.commands.CommandExecutionException;
import dev.ua.theroer.magicutils.commands.CommandLogger;
import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.CommandPlatform;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.CommandSpec;
import dev.ua.theroer.magicutils.commands.CommandThreading;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.ResolvedCommandAction;
import dev.ua.theroer.magicutils.commands.ResolvedCommandSchema;
import dev.ua.theroer.magicutils.commands.ResolvedSubCommandNode;
import dev.ua.theroer.magicutils.commands.TypeParserRegistry;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shared Brigadier-backed command registry logic.
 *
 * @param <S> command source type
 */
public class BrigadierCommandRegistry<S> {
    /**
     * Factory for wrapping a command source into an audience.
     *
     * @param <S> command source type
     */
    @FunctionalInterface
    public interface AudienceFactory<S> {
        /**
         * Creates an audience wrapper.
         *
         * @param source command source
         * @param error whether the message is an error
         * @return audience wrapper or null
         */
        Audience create(S source, boolean error);
    }

    /**
     * Resolves Brigadier argument shape for a command argument.
     *
     * @param <S> command source type
     */
    @FunctionalInterface
    public interface BrigadierArgumentResolver<S> {
        /**
         * Returns a Brigadier shape for the argument or null when unsupported.
         *
         * @param argument command argument metadata
         * @return argument shape or null
         */
        @Nullable
        BrigadierArgumentShape resolve(@NotNull CommandArgument argument);

        /**
         * Higher priority resolvers run first.
         *
         * @return resolver priority
         */
        default int priority() {
            return 0;
        }
    }

    /**
     * Brigadier-side shape for a single command argument.
     */
    public static final class BrigadierArgumentShape {
        private final ArgumentType<?> argumentType;
        private final boolean useNativeSuggestions;
        private final List<String> literalAlternatives;

        private BrigadierArgumentShape(ArgumentType<?> argumentType,
                                       boolean useNativeSuggestions,
                                       List<String> literalAlternatives) {
            this.argumentType = argumentType;
            this.useNativeSuggestions = useNativeSuggestions;
            if (literalAlternatives == null || literalAlternatives.isEmpty()) {
                this.literalAlternatives = List.of();
            } else {
                List<String> values = new ArrayList<>();
                for (String literal : literalAlternatives) {
                    if (literal != null && !literal.isBlank() && !values.contains(literal)) {
                        values.add(literal);
                    }
                }
                this.literalAlternatives = List.copyOf(values);
            }
        }

        /**
         * Creates a shape from a standard Brigadier argument type.
         *
         * @param argumentType Brigadier argument type
         * @return argument shape
         */
        public static BrigadierArgumentShape of(ArgumentType<?> argumentType) {
            return new BrigadierArgumentShape(argumentType, false, List.of());
        }

        /**
         * Creates a shape that uses native Brigadier suggestions for this argument.
         *
         * @param argumentType Brigadier argument type
         * @return argument shape
         */
        public static BrigadierArgumentShape nativeSuggestions(ArgumentType<?> argumentType) {
            return new BrigadierArgumentShape(argumentType, true, List.of());
        }

        /**
         * Returns the underlying Brigadier argument type.
         *
         * @return argument type
         */
        public ArgumentType<?> argumentType() {
            return argumentType;
        }

        /**
         * Returns whether to use native suggestions.
         *
         * @return true for native suggestions
         */
        public boolean useNativeSuggestions() {
            return useNativeSuggestions;
        }

        /**
         * Returns the list of literal alternatives for this argument.
         *
         * @return literal alternatives
         */
        public List<String> literalAlternatives() {
            return literalAlternatives;
        }

        /**
         * Returns a new shape with an added literal alternative.
         *
         * @param literal literal value
         * @return new shape
         */
        public BrigadierArgumentShape withLiteralAlternative(String literal) {
            return withLiteralAlternatives(List.of(literal));
        }

        /**
         * Returns a new shape with added literal alternatives.
         *
         * @param literals literal values
         * @return new shape
         */
        public BrigadierArgumentShape withLiteralAlternatives(String... literals) {
            if (literals == null || literals.length == 0) {
                return this;
            }
            return withLiteralAlternatives(Arrays.asList(literals));
        }

        /**
         * Returns a new shape with added literal alternatives.
         *
         * @param literals literal values
         * @return new shape
         */
        public BrigadierArgumentShape withLiteralAlternatives(List<String> literals) {
            if (literals == null || literals.isEmpty()) {
                return this;
            }
            List<String> merged = new ArrayList<>(literalAlternatives);
            for (String literal : literals) {
                if (literal != null && !literal.isBlank() && !merged.contains(literal)) {
                    merged.add(literal);
                }
            }
            return new BrigadierArgumentShape(argumentType, useNativeSuggestions, merged);
        }
    }

    /**
     * Registry for Brigadier argument resolvers.
     *
     * @param <S> command source type
     */
    public static final class BrigadierArgumentRegistry<S> {
        private final List<BrigadierArgumentResolver<S>> resolvers = new CopyOnWriteArrayList<>();

        /**
         * Creates a new Brigadier argument registry with default resolvers.
         */
        public BrigadierArgumentRegistry() {
            registerDefaultResolvers();
        }

        /**
         * Registers a custom argument resolver.
         *
         * @param resolver argument resolver
         */
        public void register(BrigadierArgumentResolver<S> resolver) {
            if (resolver == null) {
                return;
            }
            resolvers.add(resolver);
            resolvers.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        }

        /**
         * Resolves a Brigadier shape for a command argument.
         *
         * @param argument command argument metadata
         * @return argument shape
         */
        public BrigadierArgumentShape resolve(CommandArgument argument) {
            for (BrigadierArgumentResolver<S> resolver : resolvers) {
                BrigadierArgumentShape shape = resolver.resolve(argument);
                if (shape != null && shape.argumentType() != null) {
                    return shape;
                }
            }
            return BrigadierArgumentShape.of(defaultArgumentType(argument));
        }

        private void registerDefaultResolvers() {
            register(argument -> {
                Class<?> type = argument.getType();
                if (type == String.class) {
                    return BrigadierArgumentShape.of(argument.isGreedy()
                            ? StringArgumentType.greedyString()
                            : StringArgumentType.string());
                }
                if (type == Boolean.class || type == boolean.class) {
                    return BrigadierArgumentShape.of(BoolArgumentType.bool());
                }
                if (type == Integer.class || type == int.class
                        || type == Short.class || type == short.class
                        || type == Byte.class || type == byte.class) {
                    return BrigadierArgumentShape.of(IntegerArgumentType.integer());
                }
                if (type == Long.class || type == long.class) {
                    return BrigadierArgumentShape.of(LongArgumentType.longArg());
                }
                if (type == Float.class || type == float.class) {
                    return BrigadierArgumentShape.of(FloatArgumentType.floatArg());
                }
                if (type == Double.class || type == double.class) {
                    return BrigadierArgumentShape.of(DoubleArgumentType.doubleArg());
                }
                if (type.isEnum()) {
                    return BrigadierArgumentShape.of(argument.isGreedy()
                            ? StringArgumentType.greedyString()
                            : StringArgumentType.word());
                }
                if (type == BigInteger.class || type == BigDecimal.class) {
                    return BrigadierArgumentShape.of(argument.isGreedy()
                            ? StringArgumentType.greedyString()
                            : StringArgumentType.word());
                }
                return null;
            });
        }

        private static ArgumentType<?> defaultArgumentType(CommandArgument argument) {
            if (argument != null && argument.isGreedy()) {
                return StringArgumentType.greedyString();
            }
            return StringArgumentType.word();
        }
    }

    private final PrefixedLoggerCore logger;
    private final LoggerCore messageLogger;
    private final CommandManager<S> commandManager;
    private final String modName;
    private final String permissionPrefix;
    private final TaskScheduler scheduler;
    private final AudienceFactory<S> audienceFactory;
    private final BiConsumer<S, Runnable> mainThreadExecutor;
    private final BrigadierArgumentRegistry<S> brigadierArguments;

    /**
     * Creates a Brigadier-backed command registry.
     *
     * @param modName mod namespace (nullable)
     * @param permissionPrefix permission prefix (nullable)
     * @param loggerCore logger core instance
     * @param platform command platform hooks
     * @param parserRegistrar parser registrar callback (nullable)
     * @param audienceFactory audience factory (nullable)
     * @param mainThreadExecutor main thread executor (nullable)
     * @param scheduler task scheduler (nullable; shared by default)
     */
    public BrigadierCommandRegistry(String modName,
                                    String permissionPrefix,
                                    LoggerCore loggerCore,
                                    CommandPlatform<S> platform,
                                    Consumer<TypeParserRegistry<S>> parserRegistrar,
                                    AudienceFactory<S> audienceFactory,
                                    BiConsumer<S, Runnable> mainThreadExecutor,
                                    TaskScheduler scheduler) {
        this(modName, permissionPrefix, loggerCore, platform, parserRegistrar, audienceFactory,
                mainThreadExecutor, scheduler, null);
    }

    /**
     * Creates a Brigadier-backed command registry with optional Brigadier argument resolvers.
     *
     * @param modName mod namespace (nullable)
     * @param permissionPrefix permission prefix (nullable)
     * @param loggerCore logger core instance
     * @param platform command platform hooks
     * @param parserRegistrar parser registrar callback (nullable)
     * @param audienceFactory audience factory (nullable)
     * @param mainThreadExecutor main thread executor (nullable)
     * @param scheduler task scheduler (nullable; shared by default)
     * @param brigadierRegistrar Brigadier argument registrar callback (nullable)
     */
    public BrigadierCommandRegistry(String modName,
                                    String permissionPrefix,
                                    LoggerCore loggerCore,
                                    CommandPlatform<S> platform,
                                    Consumer<TypeParserRegistry<S>> parserRegistrar,
                                    AudienceFactory<S> audienceFactory,
                                    BiConsumer<S, Runnable> mainThreadExecutor,
                                    TaskScheduler scheduler,
                                    Consumer<BrigadierArgumentRegistry<S>> brigadierRegistrar) {
        if (loggerCore == null) {
            throw new IllegalArgumentException("LoggerCore instance is required");
        }
        if (platform == null) {
            throw new IllegalArgumentException("Command platform is required");
        }
        this.modName = modName != null ? modName : "";
        this.permissionPrefix = permissionPrefix != null ? permissionPrefix : "";
        this.messageLogger = loggerCore;
        this.logger = loggerCore.withPrefix("Commands", "[Commands]");
        this.scheduler = scheduler != null ? scheduler : TaskSchedulers.shared();
        this.audienceFactory = audienceFactory;
        this.mainThreadExecutor = mainThreadExecutor;
        this.brigadierArguments = new BrigadierArgumentRegistry<>();
        if (brigadierRegistrar != null) {
            brigadierRegistrar.accept(this.brigadierArguments);
        }

        CommandLogger commandLogger = new CommandLogger() {
            @Override
            public void debug(String message) {
                logger.debug().send(message);
            }

            @Override
            public void info(String message) {
                logger.info().send(message);
            }

            @Override
            public void warn(String message) {
                logger.warn().send(message);
            }

            @Override
            public void error(String message) {
                logger.error().send(message);
            }
        };

        TypeParserRegistry<S> parserRegistry = TypeParserRegistry.createWithDefaults(commandLogger);
        if (parserRegistrar != null) {
            parserRegistrar.accept(parserRegistry);
        }

        this.commandManager = new CommandManager<>(this.permissionPrefix, this.modName,
                commandLogger, platform, parserRegistry);
        logger.info().send("Command registry initialized successfully");
    }

    /**
     * Returns the command manager.
     *
     * @return command manager
     */
    public CommandManager<S> commandManager() {
        return commandManager;
    }

    /**
     * Returns whether the registry has been initialized.
     *
     * @return true when initialized
     */
    public boolean initialized() {
        return commandManager != null;
    }

    /**
     * Registers all provided commands.
     *
     * @param dispatcher Brigadier dispatcher
     * @param commands commands to register
     */
    public void registerAllCommands(CommandDispatcher<S> dispatcher, MagicCommand... commands) {
        if (commands == null) {
            return;
        }
        for (MagicCommand command : commands) {
            registerCommand(dispatcher, command);
        }
    }

    /**
     * Registers all provided command specs.
     *
     * @param dispatcher Brigadier dispatcher
     * @param specs command specs to register
     */
    public void registerAllSpecs(CommandDispatcher<S> dispatcher, CommandSpec<?>... specs) {
        if (specs == null) {
            return;
        }
        for (CommandSpec<?> spec : specs) {
            registerSpec(dispatcher, spec);
        }
    }

    /**
     * Registers a single MagicUtils command.
     *
     * @param dispatcher Brigadier dispatcher
     * @param command command instance
     */
    public void registerCommand(CommandDispatcher<S> dispatcher, MagicCommand command) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher is required");
        }
        if (command == null) {
            return;
        }

        Class<?> clazz = command.getClass();
        CommandInfo info = command.overrideInfo(clazz.getAnnotation(CommandInfo.class));
        if (info == null) {
            throw new IllegalArgumentException(InternalMessages.ERR_MISSING_COMMANDINFO.get("class", clazz.getName()));
        }

        commandManager.register(command, info);
        ResolvedCommandSchema schema = commandManager.describe(command, info);
        if (schema == null) {
            throw new IllegalStateException("Failed to resolve command schema for " + info.name());
        }

        registerCommandTree(dispatcher, info.name().toLowerCase(Locale.ROOT), schema);
        if (!modName.isEmpty()) {
            registerCommandTree(dispatcher, modName.toLowerCase(Locale.ROOT) + ":" + info.name().toLowerCase(Locale.ROOT), schema);
        }
        for (String alias : info.aliases()) {
            registerCommandTree(dispatcher, alias.toLowerCase(Locale.ROOT), schema);
            if (!modName.isEmpty()) {
                registerCommandTree(dispatcher, modName.toLowerCase(Locale.ROOT) + ":" + alias.toLowerCase(Locale.ROOT), schema);
            }
        }

        logger.info().send(InternalMessages.SYS_COMMAND_REGISTERED.get("command", info.name(),
                "aliases", Arrays.toString(info.aliases())));
    }

    /**
     * Registers a compatibility command spec by converting it to a {@link MagicCommand}.
     *
     * @param dispatcher Brigadier dispatcher
     * @param spec command spec
     */
    public void registerSpec(CommandDispatcher<S> dispatcher, CommandSpec<?> spec) {
        if (spec == null) {
            return;
        }
        registerCommand(dispatcher, MagicCommand.fromSpec(spec));
    }

    private void registerCommandTree(CommandDispatcher<S> dispatcher, String label, ResolvedCommandSchema schema) {
        if (label == null || label.isEmpty()) {
            return;
        }
        if (dispatcher.getRoot().getChild(label) != null) {
            logger.warn().send("Command already registered: " + label);
            return;
        }

        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal(label)
                .requires(source -> commandManager.canAccessCommand(label, source, null));
        root.executes(context -> executeFromContext(label, context));

        if (schema.directAction() != null) {
            attachActionSyntax(root, label, schema.directAction());
        }
        attachSubCommandChildren(root, label, List.of(), schema.subCommands().children());

        dispatcher.register(root);
    }

    private void attachSubCommandChildren(ArgumentBuilder<S, ?> parent,
                                          String label,
                                          List<String> pathSegments,
                                          List<ResolvedSubCommandNode> children) {
        if (children == null || children.isEmpty()) {
            return;
        }
        for (ResolvedSubCommandNode child : children) {
            if (child == null) {
                continue;
            }
            attachSubCommandBranch(parent, label, pathSegments, child, child.name());
            for (String alias : child.aliases()) {
                if (alias != null && !alias.isBlank()) {
                    attachSubCommandBranch(parent, label, pathSegments, child, alias);
                }
            }
        }
    }

    private void attachSubCommandBranch(ArgumentBuilder<S, ?> parent,
                                        String label,
                                        List<String> pathSegments,
                                        ResolvedSubCommandNode node,
                                        String branchLabel) {
        if (branchLabel == null || branchLabel.isBlank()) {
            return;
        }
        List<String> branchPath = new ArrayList<>(pathSegments);
        branchPath.add(branchLabel);

        LiteralArgumentBuilder<S> literal = LiteralArgumentBuilder.<S>literal(branchLabel)
                .requires(source -> commandManager.canAccessSubCommandPath(label, source, branchPath))
                .executes(context -> executeFromContext(label, context));

        if (node.action() != null) {
            attachActionSyntax(literal, label, node.action());
        }
        attachSubCommandChildren(literal, label, branchPath, node.children());
        parent.then(literal);
    }

    private void attachActionSyntax(ArgumentBuilder<S, ?> parent, String label, ResolvedCommandAction action) {
        List<CommandArgument> positionalArguments = visiblePositionalArguments(action);
        List<CommandArgument> optionArguments = visibleOptionArguments(action);
        if (!positionalArguments.isEmpty() || !optionArguments.isEmpty()) {
            buildActionSyntax(parent, label, positionalArguments, optionArguments, 0, Set.of(), true, true);
        }
        if (needsLegacyFallback(action, positionalArguments, optionArguments)) {
            attachLegacyFallback(parent, label);
        }
    }

    private List<CommandArgument> visiblePositionalArguments(ResolvedCommandAction action) {
        List<CommandArgument> arguments = new ArrayList<>();
        for (CommandArgument argument : action.arguments()) {
            if (argument == null || argument.isOption() || commandManager.isSenderArgument(argument)) {
                continue;
            }
            arguments.add(argument);
        }
        return arguments;
    }

    private List<CommandArgument> visibleOptionArguments(ResolvedCommandAction action) {
        List<CommandArgument> arguments = new ArrayList<>();
        for (CommandArgument argument : action.arguments()) {
            if (argument == null || !argument.isOption() || commandManager.isSenderArgument(argument)) {
                continue;
            }
            arguments.add(argument);
        }
        return arguments;
    }

    private void buildActionSyntax(ArgumentBuilder<S, ?> parent,
                                   String label,
                                   List<CommandArgument> positionalArguments,
                                   List<CommandArgument> optionArguments,
                                   int positionalIndex,
                                   Set<CommandArgument> usedOptions,
                                   boolean optionsEnabled,
                                   boolean attachOptions) {
        if (positionalIndex >= positionalArguments.size()) {
            if (optionsEnabled && attachOptions) {
                attachOptionBranches(parent, label, positionalArguments, optionArguments,
                        positionalIndex, usedOptions, true);
            }
            return;
        }

        CommandArgument argument = positionalArguments.get(positionalIndex);
        if (argument == null) {
            return;
        }

        if (isOptional(argument)) {
            buildActionSyntax(parent, label, positionalArguments, optionArguments,
                    positionalIndex + 1, usedOptions, optionsEnabled, false);
        }

        if (optionsEnabled && attachOptions) {
            attachOptionBranches(parent, label, positionalArguments, optionArguments,
                    positionalIndex, usedOptions, true);
        }

        attachArgumentBranch(parent, createArgumentNode(argument, label), label,
                positionalArguments, optionArguments, positionalIndex, usedOptions, optionsEnabled);
        for (LiteralArgumentBuilder<S> alternative : createAlternativeLiteralNodes(argument, label)) {
            attachArgumentBranch(parent, alternative, label,
                    positionalArguments, optionArguments, positionalIndex, usedOptions, optionsEnabled);
        }
    }

    private RequiredArgumentBuilder<S, ?> createArgumentNode(CommandArgument argument, String label) {
        BrigadierArgumentShape shape = brigadierArguments.resolve(argument);
        RequiredArgumentBuilder<S, ?> builder = argument(argumentNodeName(argument), shape.argumentType())
                .executes(context -> executeFromContext(label, context));
        if (!shape.useNativeSuggestions() || !argument.getSuggestions().isEmpty()) {
            builder.suggests((context, builder1) -> suggest(label, context.getSource(), builder1));
        }
        return builder;
    }

    private List<LiteralArgumentBuilder<S>> createAlternativeLiteralNodes(CommandArgument argument, String label) {
        BrigadierArgumentShape shape = brigadierArguments.resolve(argument);
        if (shape.literalAlternatives().isEmpty()) {
            return List.of();
        }
        List<LiteralArgumentBuilder<S>> nodes = new ArrayList<>();
        for (String literal : shape.literalAlternatives()) {
            nodes.add(LiteralArgumentBuilder.<S>literal(literal)
                    .executes(context -> executeFromContext(label, context)));
        }
        return nodes;
    }

    private void attachArgumentBranch(ArgumentBuilder<S, ?> parent,
                                      ArgumentBuilder<S, ?> branch,
                                      String label,
                                      List<CommandArgument> positionalArguments,
                                      List<CommandArgument> optionArguments,
                                      int positionalIndex,
                                      Set<CommandArgument> usedOptions,
                                      boolean optionsEnabled) {
        if (!(branch instanceof LiteralArgumentBuilder || branch instanceof RequiredArgumentBuilder)) {
            return;
        }
        CommandArgument currentArgument = positionalArguments.get(positionalIndex);
        if (!currentArgument.isGreedy()) {
            buildActionSyntax(branch, label, positionalArguments, optionArguments,
                    positionalIndex + 1, usedOptions, optionsEnabled, true);
        }
        parent.then(branch);
    }

    private void attachOptionBranches(ArgumentBuilder<S, ?> parent,
                                      String label,
                                      List<CommandArgument> positionalArguments,
                                      List<CommandArgument> optionArguments,
                                      int positionalIndex,
                                      Set<CommandArgument> usedOptions,
                                      boolean optionsEnabled) {
        if (optionArguments.isEmpty()) {
            return;
        }

        for (CommandArgument option : optionArguments) {
            if (option == null || usedOptions.contains(option)) {
                continue;
            }
            attachOptionBranch(parent, label, option, positionalArguments, optionArguments,
                    positionalIndex, usedOptions, optionsEnabled);
        }

        if (optionsEnabled && positionalIndex < positionalArguments.size()) {
            LiteralArgumentBuilder<S> terminator = LiteralArgumentBuilder.<S>literal("--");
            buildActionSyntax(terminator, label, positionalArguments, optionArguments,
                    positionalIndex, usedOptions, false, true);
            parent.then(terminator);
        }
    }

    private void attachOptionBranch(ArgumentBuilder<S, ?> parent,
                                    String label,
                                    CommandArgument option,
                                    List<CommandArgument> positionalArguments,
                                    List<CommandArgument> optionArguments,
                                    int positionalIndex,
                                    Set<CommandArgument> usedOptions,
                                    boolean optionsEnabled) {
        Set<CommandArgument> nextUsed = new HashSet<>(usedOptions);
        nextUsed.add(option);

        for (String token : optionTokens(option)) {
            LiteralArgumentBuilder<S> tokenNode = LiteralArgumentBuilder.<S>literal(token);
            if (option.isFlag()) {
                tokenNode.executes(context -> executeFromContext(label, context));
                buildActionSyntax(tokenNode, label, positionalArguments, optionArguments,
                        positionalIndex, nextUsed, optionsEnabled, true);
                parent.then(tokenNode);
                continue;
            }

            attachOptionValueBranches(tokenNode, label, option, positionalArguments, optionArguments,
                    positionalIndex, nextUsed, optionsEnabled);
            parent.then(tokenNode);
        }
    }

    private void attachOptionValueBranches(ArgumentBuilder<S, ?> parent,
                                           String label,
                                           CommandArgument option,
                                           List<CommandArgument> positionalArguments,
                                           List<CommandArgument> optionArguments,
                                           int positionalIndex,
                                           Set<CommandArgument> usedOptions,
                                           boolean optionsEnabled) {
        attachOptionValueBranch(parent, createArgumentNode(option, label), label,
                positionalArguments, optionArguments, positionalIndex, usedOptions, optionsEnabled);
        for (LiteralArgumentBuilder<S> alternative : createAlternativeLiteralNodes(option, label)) {
            attachOptionValueBranch(parent, alternative, label,
                    positionalArguments, optionArguments, positionalIndex, usedOptions, optionsEnabled);
        }
    }

    private void attachOptionValueBranch(ArgumentBuilder<S, ?> parent,
                                         ArgumentBuilder<S, ?> branch,
                                         String label,
                                         List<CommandArgument> positionalArguments,
                                         List<CommandArgument> optionArguments,
                                         int positionalIndex,
                                         Set<CommandArgument> usedOptions,
                                         boolean optionsEnabled) {
        if (!(branch instanceof LiteralArgumentBuilder || branch instanceof RequiredArgumentBuilder)) {
            return;
        }
        buildActionSyntax(branch, label, positionalArguments, optionArguments,
                positionalIndex, usedOptions, optionsEnabled, true);
        parent.then(branch);
    }

    private List<String> optionTokens(CommandArgument option) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String longName : option.getLongOptionNames()) {
            if (longName != null && !longName.isBlank()) {
                tokens.add("--" + longName);
            }
        }
        for (String shortName : option.getShortOptionNames()) {
            if (shortName != null && !shortName.isBlank()) {
                tokens.add("-" + shortName);
            }
        }
        return new ArrayList<>(tokens);
    }

    private String argumentNodeName(CommandArgument argument) {
        String name = argument != null ? argument.getName() : null;
        return (name != null && !name.isBlank()) ? name : "arg";
    }

    private boolean needsLegacyFallback(ResolvedCommandAction action,
                                        List<CommandArgument> positionalArguments,
                                        List<CommandArgument> optionArguments) {
        boolean hasValueOptions = false;
        boolean sawOptionalPositional = false;

        for (CommandArgument argument : optionArguments) {
            if (argument != null && !argument.isFlag()) {
                hasValueOptions = true;
            }
        }

        for (int i = 0; i < positionalArguments.size(); i++) {
            CommandArgument argument = positionalArguments.get(i);
            if (argument.isGreedy() && i < positionalArguments.size() - 1) {
                return true;
            }
            if (isOptional(argument)) {
                sawOptionalPositional = true;
                continue;
            }
            if (sawOptionalPositional) {
                return true;
            }
        }

        return hasValueOptions;
    }

    private boolean isOptional(CommandArgument argument) {
        return argument != null && (argument.isOptional() || argument.getDefaultValue() != null);
    }

    private void attachLegacyFallback(ArgumentBuilder<S, ?> parent, String label) {
        RequiredArgumentBuilder<S, String> builder = RequiredArgumentBuilder.<S, String>argument(
                        "args", StringArgumentType.greedyString())
                .suggests((context, suggestions) -> suggest(label, context.getSource(), suggestions))
                .executes(context -> executeFromContext(label, context));
        parent.then(builder);
    }

    @SuppressWarnings("unchecked")
    private <T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<?> type) {
        return RequiredArgumentBuilder.argument(name, (ArgumentType<T>) type);
    }

    private int executeFromContext(String label, CommandContext<S> context) throws CommandSyntaxException {
        List<String> tokens = parseArgsForExecution(normalizeInput(context.getInput()));
        if (!tokens.isEmpty()) {
            tokens.remove(0);
        }
        return executeCommand(label, context.getSource(), tokens);
    }

    private static String normalizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.charAt(0) == '/' ? input.substring(1) : input;
    }

    private int executeCommand(String label, S source, List<String> args) throws CommandSyntaxException {
        try {
            CommandThreading threading = commandManager.resolveThreading(label, args);
            if (threading == CommandThreading.ASYNC) {
                dispatchAsync(label, source, args);
                return 1;
            }
            CommandResult result = commandManager.executeOrThrow(label, source, args);
            sendResult(source, result);
            return result.isSuccess() ? 1 : 0;
        } catch (CommandExecutionException e) {
            messageLogger.error().send("Error executing command " + label + ": " + e.getMessage());
            throw createSyntaxException(e.getUserMessage());
        } catch (Exception e) {
            messageLogger.error().send("Error executing command " + label + ": " + e.getMessage());
            throw createSyntaxException(InternalMessages.CMD_INTERNAL_ERROR.get());
        }
    }

    private void dispatchAsync(String label, S source, List<String> args) {
        if (scheduler == null) {
            CommandResult result = commandManager.execute(label, source, args);
            sendResult(source, result);
            return;
        }
        scheduler.cpu().execute(() -> {
            CommandResult result;
            try {
                result = commandManager.execute(label, source, args);
            } catch (Exception e) {
                messageLogger.error().send("Error executing command " + label + ": " + e.getMessage());
                result = CommandResult.failure(InternalMessages.CMD_INTERNAL_ERROR.get());
            }
            CommandResult finalResult = result;
            runOnMain(source, () -> sendResult(source, finalResult));
        });
    }

    /**
     * Runs a task on the main thread when an executor is provided.
     *
     * @param source command source
     * @param task task to run
     */
    protected void runOnMain(S source, Runnable task) {
        if (task == null) {
            return;
        }
        if (mainThreadExecutor != null) {
            mainThreadExecutor.accept(source, task);
            return;
        }
        task.run();
    }

    private void sendResult(S source, CommandResult result) {
        if (result == null || !result.isSendMessage()) {
            return;
        }
        String message = result.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        Audience audience = audienceFactory != null ? audienceFactory.create(source, !result.isSuccess()) : null;
        if (audience == null) {
            return;
        }
        if (result.isSuccess()) {
            messageLogger.success().target(LogTarget.CHAT).to(audience).send(message);
        } else {
            messageLogger.error().target(LogTarget.CHAT).to(audience).send(message);
        }
    }

    private CommandSyntaxException createSyntaxException(String message) {
        String safe = message != null && !message.isBlank()
                ? message
                : InternalMessages.CMD_INTERNAL_ERROR.get();
        Message brigMessage = new LiteralMessage(safe);
        return new SimpleCommandExceptionType(brigMessage).create();
    }

    private CompletableFuture<Suggestions> suggest(String label, S source, SuggestionsBuilder builder) {
        String input = builder.getInput();
        int start = builder.getStart();
        String remaining = (input != null && start >= 0 && start <= input.length())
                ? input.substring(start)
                : builder.getRemaining();
        TokenizedInput tokenized = tokenize(remaining, true);
        List<String> args = tokenized.tokens();
        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + tokenized.currentTokenStart());
        List<String> suggestions = commandManager.getSuggestions(label, source, args);
        for (String suggestion : suggestions) {
            if (suggestion != null && !suggestion.trim().isEmpty()) {
                offsetBuilder.suggest(suggestion);
            }
        }
        return offsetBuilder.buildFuture();
    }

    private static List<String> parseArgsForExecution(String input) {
        return new ArrayList<>(tokenize(input, false).tokens());
    }

    private static TokenizedInput tokenize(String input, boolean includeTrailingEmpty) {
        if (input == null || input.isEmpty()) {
            if (includeTrailingEmpty) {
                return new TokenizedInput(List.of(""), 0);
            }
            return new TokenizedInput(List.of(), 0);
        }

        if (input.trim().isEmpty()) {
            return includeTrailingEmpty
                    ? new TokenizedInput(List.of(""), input.length())
                    : new TokenizedInput(List.of(), 0);
        }

        List<String> tokens = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        StringReader reader = new StringReader(input);

        while (reader.canRead()) {
            reader.skipWhitespace();
            if (!reader.canRead()) {
                break;
            }
            int start = reader.getCursor();
            try {
                String token = StringArgumentType.string().parse(reader);
                if (reader.getCursor() == start) {
                    String rest = input.substring(start);
                    tokens.add(rest);
                    starts.add(start);
                    reader.setCursor(input.length());
                    break;
                }
                tokens.add(token);
                starts.add(start);
            } catch (CommandSyntaxException e) {
                String rest = input.substring(start);
                tokens.add(rest);
                starts.add(start);
                reader.setCursor(input.length());
                break;
            }
        }

        if (includeTrailingEmpty && input.length() > 0 && Character.isWhitespace(input.charAt(input.length() - 1))) {
            tokens.add("");
            starts.add(input.length());
        }

        if (tokens.isEmpty() && includeTrailingEmpty) {
            tokens.add("");
            starts.add(input.length());
        }

        int offset = starts.isEmpty() ? 0 : starts.get(starts.size() - 1);
        return new TokenizedInput(tokens, offset);
    }

    private record TokenizedInput(List<String> tokens, int currentTokenStart) {
    }
}
