package dev.ua.theroer.magicutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.parsers.PlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.WorldTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles registration and initialization of commands in Fabric.
 */
public final class CommandRegistry {
    private static final Map<String, CommandRegistry> REGISTRIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean ADAPTER_REGISTERED = new AtomicBoolean();
    private static final AtomicInteger ADAPTER_OP_LEVEL = new AtomicInteger(2);
    private static volatile CommandRegistry defaultRegistry;

    private final PrefixedLogger logger;
    private final Logger messageLogger;
    private final CommandManager<ServerCommandSource> commandManager;
    private final FabricCommandPlatform platform;
    private final String modName;
    private final String permissionPrefix;
    private final int opLevel;

    private CommandRegistry(String modName, String permissionPrefix, Logger loggerInstance, int opLevel) {
        if (loggerInstance == null) {
            throw new IllegalArgumentException("Logger instance is required");
        }
        this.modName = modName != null ? modName : "";
        this.permissionPrefix = permissionPrefix != null ? permissionPrefix : "";
        this.opLevel = opLevel;
        this.logger = loggerInstance.withPrefix("Commands", "[Commands]");
        this.messageLogger = loggerInstance;

        CommandLogger commandLogger = new CommandLogger() {
            @Override
            public void debug(String message) {
                logger.debug(message);
            }

            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }

            @Override
            public void error(String message) {
                logger.error(message);
            }
        };

        this.platform = new FabricCommandPlatform(opLevel);
        TypeParserRegistry<ServerCommandSource> parserRegistry = TypeParserRegistry.createWithDefaults(commandLogger);
        parserRegistry.register(new PlayerTypeParser(logger));
        parserRegistry.register(new WorldTypeParser(logger));

        this.commandManager = new CommandManager<>(this.permissionPrefix, this.modName,
                commandLogger, platform, parserRegistry);
        registerMagicSenderAdapter(opLevel);
        logger.info("Command registry initialized successfully");
    }

    /**
     * Initializes the command registry with the mod name and permission prefix.
     *
     * @param modName          mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance   magicutils logger
     */
    public static void initialize(String modName, String permissionPrefix, Logger loggerInstance) {
        createDefault(modName, permissionPrefix, loggerInstance, 2);
    }

    /**
     * Initializes the command registry with an explicit op level.
     *
     * @param modName          mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance   magicutils logger
     * @param opLevel          permission level to treat as OP
     */
    public static void initialize(String modName, String permissionPrefix, Logger loggerInstance, int opLevel) {
        createDefault(modName, permissionPrefix, loggerInstance, opLevel);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param modName          mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance   magicutils logger
     * @return registry instance
     */
    public static CommandRegistry create(String modName, String permissionPrefix, Logger loggerInstance) {
        return create(modName, permissionPrefix, loggerInstance, 2, false);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param modName          mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance   magicutils logger
     * @param opLevel          permission level to treat as OP
     * @return registry instance
     */
    public static CommandRegistry create(String modName,
                                         String permissionPrefix,
                                         Logger loggerInstance,
                                         int opLevel) {
        return create(modName, permissionPrefix, loggerInstance, opLevel, false);
    }

    /**
     * Creates a registry instance and sets it as default.
     *
     * @param modName          mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance   magicutils logger
     * @param opLevel          permission level to treat as OP
     * @return registry instance
     */
    public static CommandRegistry createDefault(String modName,
                                                String permissionPrefix,
                                                Logger loggerInstance,
                                                int opLevel) {
        return create(modName, permissionPrefix, loggerInstance, opLevel, true);
    }

    private static CommandRegistry create(String modName,
                                          String permissionPrefix,
                                          Logger loggerInstance,
                                          int opLevel,
                                          boolean makeDefault) {
        CommandRegistry registry = new CommandRegistry(modName, permissionPrefix, loggerInstance, opLevel);
        REGISTRIES.put(registryKey(modName), registry);
        if (makeDefault || defaultRegistry == null) {
            defaultRegistry = registry;
        }
        return registry;
    }

    /**
     * Returns the registry instance by mod name.
     *
     * @param modName mod name
     * @return registry or null
     */
    public static CommandRegistry get(String modName) {
        if (modName == null) {
            return null;
        }
        return REGISTRIES.get(registryKey(modName));
    }

    /**
     * Returns the default command manager.
     *
     * @return command manager or null
     */
    public static CommandManager<ServerCommandSource> getCommandManager() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.commandManager : null;
    }

    /**
     * Returns the instance command manager.
     *
     * @return command manager
     */
    public CommandManager<ServerCommandSource> commandManager() {
        return commandManager;
    }

    /**
     * Returns true if the default registry is initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return defaultRegistry != null && defaultRegistry.initialized();
    }

    /**
     * Returns true if this registry is initialized.
     *
     * @return true if initialized
     */
    public boolean initialized() {
        return commandManager != null;
    }

    /**
     * Registers multiple commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param commands commands to register
     */
    public static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher, MagicCommand... commands) {
        requireDefault().registerAllCommands(dispatcher, commands);
    }

    /**
     * Registers multiple builder-defined commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param specs command specs to register
     */
    public static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec<?>... specs) {
        requireDefault().registerAllSpecs(dispatcher, specs);
    }

    /**
     * Registers a single command.
     *
     * @param dispatcher dispatcher to register on
     * @param command command to register
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, MagicCommand command) {
        requireDefault().registerCommand(dispatcher, command);
    }

    /**
     * Registers a single builder-defined command.
     *
     * @param dispatcher dispatcher to register on
     * @param spec command spec to register
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec<?> spec) {
        requireDefault().registerSpec(dispatcher, spec);
    }

    /**
     * Registers multiple commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param commands commands to register
     */
    public void registerAllCommands(CommandDispatcher<ServerCommandSource> dispatcher, MagicCommand... commands) {
        if (commands == null) {
            return;
        }
        for (MagicCommand command : commands) {
            registerCommand(dispatcher, command);
        }
    }

    /**
     * Registers multiple builder-defined commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param specs command specs to register
     */
    public void registerAllSpecs(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec<?>... specs) {
        if (specs == null) {
            return;
        }
        for (CommandSpec<?> spec : specs) {
            registerSpec(dispatcher, spec);
        }
    }

    /**
     * Registers a single command.
     *
     * @param dispatcher dispatcher to register on
     * @param command command to register
     */
    public void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher, MagicCommand command) {
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

        registerLiteral(dispatcher, info.name().toLowerCase(Locale.ROOT));
        if (!modName.isEmpty()) {
            registerLiteral(dispatcher, modName.toLowerCase(Locale.ROOT) + ":" + info.name().toLowerCase(Locale.ROOT));
        }
        for (String alias : info.aliases()) {
            registerLiteral(dispatcher, alias.toLowerCase(Locale.ROOT));
            if (!modName.isEmpty()) {
                registerLiteral(dispatcher, modName.toLowerCase(Locale.ROOT) + ":" + alias.toLowerCase(Locale.ROOT));
            }
        }

        logger.info(InternalMessages.SYS_COMMAND_REGISTERED.get("command", info.name(),
                "aliases", Arrays.toString(info.aliases())));
    }

    /**
     * Registers a single builder-defined command.
     *
     * @param dispatcher dispatcher to register on
     * @param spec command spec to register
     */
    public void registerSpec(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec<?> spec) {
        if (spec == null) {
            return;
        }
        registerCommand(dispatcher, new DynamicCommand(spec));
    }

    private void registerLiteral(CommandDispatcher<ServerCommandSource> dispatcher, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        if (dispatcher.getRoot().getChild(label) != null) {
            logger.warn("Command already registered: " + label);
            return;
        }

        LiteralArgumentBuilder<ServerCommandSource> root = LiteralArgumentBuilder.<ServerCommandSource>literal(label)
                .requires(source -> commandManager.canAccessCommand(label, source, null));
        root.executes(context -> executeCommand(label, context.getSource(), Collections.emptyList()));

        RequiredArgumentBuilder<ServerCommandSource, String> argsNode = RequiredArgumentBuilder
                .<ServerCommandSource, String>argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> suggest(label, context.getSource(), builder))
                .executes(context -> {
                    String input = StringArgumentType.getString(context, "args");
                    List<String> args = parseArgsForExecution(input);
                    return executeCommand(label, context.getSource(), args);
                });
        root.then(argsNode);

        dispatcher.register(root);
    }

    private int executeCommand(String label, ServerCommandSource source, List<String> args)
            throws CommandSyntaxException {
        try {
            CommandResult result = commandManager.executeOrThrow(label, source, args);
            if (result.isSendMessage() && result.getMessage() != null && !result.getMessage().isEmpty()) {
                if (result.isSuccess()) {
                    messageLogger.success().target(LogTarget.CHAT).to(source).send(result.getMessage());
                } else {
                    messageLogger.error().target(LogTarget.CHAT).toError(source).send(result.getMessage());
                }
            }
            return result.isSuccess() ? 1 : 0;
        } catch (CommandExecutionException e) {
            messageLogger.error("Error executing command " + label + ": " + e.getMessage());
            throw createSyntaxException(e.getUserMessage());
        } catch (Exception e) {
            messageLogger.error("Error executing command " + label + ": " + e.getMessage());
            throw createSyntaxException(InternalMessages.CMD_INTERNAL_ERROR.get());
        }
    }

    private CommandSyntaxException createSyntaxException(String message) {
        String safe = message != null && !message.isBlank()
                ? message
                : InternalMessages.CMD_INTERNAL_ERROR.get();
        Message brigMessage = new LiteralMessage(safe);
        return new SimpleCommandExceptionType(brigMessage).create();
    }

    private CompletableFuture<Suggestions> suggest(String label, ServerCommandSource source,
            SuggestionsBuilder builder) {
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

    private static void registerMagicSenderAdapter(int opLevel) {
        ADAPTER_OP_LEVEL.set(opLevel);
        if (!ADAPTER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MagicSenderAdapters.register("fabric", new MagicSenderAdapter() {
            @Override
            public boolean supports(Object sender) {
                return sender instanceof ServerCommandSource || sender instanceof ServerPlayerEntity;
            }

            @Override
            public MagicSender wrap(Object sender) {
                int level = resolveAdapterOpLevel();
                if (sender instanceof ServerCommandSource source) {
                    return FabricCommandPlatform.wrapMagicSender(source, level);
                }
                if (sender instanceof ServerPlayerEntity player) {
                    return FabricCommandPlatform.wrapMagicSender(player.getCommandSource(), level);
                }
                return null;
            }
        });
    }

    private static int resolveAdapterOpLevel() {
        CommandRegistry registry = defaultRegistry;
        if (registry != null) {
            return registry.opLevel;
        }
        return ADAPTER_OP_LEVEL.get();
    }

    private static CommandRegistry requireDefault() {
        CommandRegistry registry = defaultRegistry;
        if (registry == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        return registry;
    }

    private static String registryKey(String modName) {
        String name = modName != null ? modName.trim().toLowerCase(Locale.ROOT) : "";
        return name.isEmpty() ? "default" : name;
    }
}
