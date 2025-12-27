package dev.ua.theroer.magicutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.parsers.PlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.WorldTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import lombok.Getter;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Handles registration and initialization of commands in Fabric.
 */
public final class CommandRegistry {
    private static PrefixedLogger logger;
    private static Logger messageLogger;

    @Getter
    private static CommandManager<ServerCommandSource> commandManager;
    private static FabricCommandPlatform platform;
    private static String modName;

    private CommandRegistry() {
    }

    /**
     * Initializes the command registry with the mod name and permission prefix.
     *
     * @param modName          mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance   magicutils logger
     */
    public static void initialize(String modName, String permissionPrefix, Logger loggerInstance) {
        initialize(modName, permissionPrefix, loggerInstance, 2);
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
        CommandRegistry.modName = modName != null ? modName : "";
        if (loggerInstance == null) {
            throw new IllegalArgumentException("Logger instance is required");
        }
        logger = loggerInstance.withPrefix("Commands", "[Commands]");
        messageLogger = loggerInstance;

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

        platform = new FabricCommandPlatform(opLevel);
        TypeParserRegistry<ServerCommandSource> parserRegistry = TypeParserRegistry.createWithDefaults(commandLogger);
        parserRegistry.register(new PlayerTypeParser(logger));
        parserRegistry.register(new WorldTypeParser(logger));

        CommandRegistry.commandManager = new CommandManager<>(permissionPrefix, CommandRegistry.modName,
                commandLogger, platform, parserRegistry);
        logger.info("Command registry initialized successfully");
    }

    /**
     * Registers multiple commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param commands commands to register
     */
    public static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher, MagicCommand... commands) {
        if (commandManager == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher is required");
        }
        if (commands == null) {
            return;
        }
        for (MagicCommand command : commands) {
            register(dispatcher, command);
        }
    }

    /**
     * Registers multiple builder-defined commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param specs command specs to register
     */
    public static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec<?>... specs) {
        if (commandManager == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher is required");
        }
        if (specs == null) {
            return;
        }
        for (CommandSpec<?> spec : specs) {
            register(dispatcher, spec);
        }
    }

    /**
     * Registers a single command.
     *
     * @param dispatcher dispatcher to register on
     * @param command command to register
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, MagicCommand command) {
        if (commandManager == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
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
        if (modName != null && !modName.isEmpty()) {
            registerLiteral(dispatcher, modName.toLowerCase(Locale.ROOT) + ":" + info.name().toLowerCase(Locale.ROOT));
        }
        for (String alias : info.aliases()) {
            registerLiteral(dispatcher, alias.toLowerCase(Locale.ROOT));
            if (modName != null && !modName.isEmpty()) {
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
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec<?> spec) {
        if (spec == null) {
            return;
        }
        register(dispatcher, new DynamicCommand(spec));
    }

    private static void registerLiteral(CommandDispatcher<ServerCommandSource> dispatcher, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        if (dispatcher.getRoot().getChild(label) != null) {
            logger.warn("Command already registered: " + label);
            return;
        }

        LiteralArgumentBuilder<ServerCommandSource> root = LiteralArgumentBuilder.<ServerCommandSource>literal(label);
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

    private static int executeCommand(String label, ServerCommandSource source, List<String> args) {
        try {
            CommandResult result = commandManager.execute(label, source, args);
            if (result.isSendMessage() && result.getMessage() != null && !result.getMessage().isEmpty()) {
                if (result.isSuccess()) {
                    messageLogger.success().to(source).send(result.getMessage());
                } else {
                    messageLogger.error().toError(source).send(result.getMessage());
                }
            }
            return result.isSuccess() ? 1 : 0;
        } catch (Exception e) {
            messageLogger.error("Error executing command " + label + ": " + e.getMessage());
            messageLogger.error().toError(source).send(InternalMessages.CMD_INTERNAL_ERROR.get());
            e.printStackTrace();
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggest(String label, ServerCommandSource source,
            SuggestionsBuilder builder) {
        List<String> args = parseArgsForSuggestions(builder.getRemaining());
        List<String> suggestions = commandManager.getSuggestions(label, source, args);
        for (String suggestion : suggestions) {
            if (suggestion != null && !suggestion.trim().isEmpty()) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static List<String> parseArgsForSuggestions(String input) {
        if (input == null) {
            return new ArrayList<>();
        }
        if (input.isEmpty()) {
            return new ArrayList<>(List.of(""));
        }
        return new ArrayList<>(Arrays.asList(input.split(" ", -1)));
    }

    private static List<String> parseArgsForExecution(String input) {
        if (input == null || input.isBlank()) {
            return new ArrayList<>();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(trimmed.split("\\s+")));
    }
}
