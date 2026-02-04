package dev.ua.theroer.magicutils.commands.brigadier;

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
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandExecutionException;
import dev.ua.theroer.magicutils.commands.CommandLogger;
import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.CommandPlatform;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.CommandSpec;
import dev.ua.theroer.magicutils.commands.CommandThreading;
import dev.ua.theroer.magicutils.commands.DynamicCommand;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.TypeParserRegistry;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.LogTarget;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

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

    private final PrefixedLoggerCore logger;
    private final LoggerCore messageLogger;
    private final CommandManager<S> commandManager;
    private final CommandPlatform<S> platform;
    private final String modName;
    private final String permissionPrefix;
    private final TaskScheduler scheduler;
    private final AudienceFactory<S> audienceFactory;
    private final BiConsumer<S, Runnable> mainThreadExecutor;

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

        this.platform = platform;
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

        logger.info().send(InternalMessages.SYS_COMMAND_REGISTERED.get("command", info.name(),
                "aliases", Arrays.toString(info.aliases())));
    }

    /**
     * Registers a command spec by wrapping it into a dynamic command.
     *
     * @param dispatcher Brigadier dispatcher
     * @param spec command spec
     */
    public void registerSpec(CommandDispatcher<S> dispatcher, CommandSpec<?> spec) {
        if (spec == null) {
            return;
        }
        registerCommand(dispatcher, new DynamicCommand(spec));
    }

    private void registerLiteral(CommandDispatcher<S> dispatcher, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        if (dispatcher.getRoot().getChild(label) != null) {
            logger.warn().send("Command already registered: " + label);
            return;
        }

        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal(label)
                .requires(source -> commandManager.canAccessCommand(label, source, null));
        root.executes(context -> executeCommand(label, context.getSource(), Collections.emptyList()));

        RequiredArgumentBuilder<S, String> argsNode = RequiredArgumentBuilder
                .<S, String>argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> suggest(label, context.getSource(), builder))
                .executes(context -> {
                    String input = StringArgumentType.getString(context, "args");
                    List<String> args = parseArgsForExecution(input);
                    return executeCommand(label, context.getSource(), args);
                });
        root.then(argsNode);

        dispatcher.register(root);
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
