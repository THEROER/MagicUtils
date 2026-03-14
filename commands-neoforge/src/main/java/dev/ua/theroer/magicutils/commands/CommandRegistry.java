package dev.ua.theroer.magicutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.ua.theroer.magicutils.commands.brigadier.BrigadierCommandRegistry;
import dev.ua.theroer.magicutils.commands.brigadier.BrigadierCommandRegistry.BrigadierArgumentShape;
import dev.ua.theroer.magicutils.commands.parsers.PlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.WorldTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import dev.ua.theroer.magicutils.platform.neoforge.NeoForgeCommandAudience;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles registration and initialization of commands in NeoForge.
 */
public final class CommandRegistry extends BrigadierCommandRegistry<CommandSourceStack> {
    private static final Map<String, CommandRegistry> REGISTRIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean ADAPTER_REGISTERED = new AtomicBoolean();
    private static final AtomicInteger ADAPTER_OP_LEVEL = new AtomicInteger(2);
    private static volatile CommandRegistry defaultRegistry;

    private final int opLevel;

    private CommandRegistry(String modId, String permissionPrefix, LoggerCore loggerCore, int opLevel) {
        super(modId,
                permissionPrefix,
                requireLoggerCore(loggerCore),
                new NeoForgeCommandPlatform(opLevel),
                registry -> {
                    PrefixedLoggerCore parserLogger = loggerCore.withPrefix("Commands", "[Commands]");
                    registry.register(new PlayerTypeParser(parserLogger));
                    registry.register(new WorldTypeParser(parserLogger));
                },
                (source, error) -> new NeoForgeCommandAudience(source, false,
                        error ? NeoForgeCommandAudience.Mode.ERROR : NeoForgeCommandAudience.Mode.FEEDBACK),
                (source, task) -> {
                    if (task == null) {
                        return;
                    }
                    if (source == null || source.getServer() == null || isServerThread(source)) {
                        task.run();
                        return;
                    }
                    source.getServer().execute(task);
                },
                TaskSchedulers.shared(),
                registry -> registry.register(new BrigadierArgumentResolver<>() {
                    @Override
                    public BrigadierArgumentShape resolve(CommandArgument argument) {
                        if (argument == null) {
                            return null;
                        }
                        Class<?> type = argument.getType();
                        if (type == ServerPlayer.class) {
                            return BrigadierArgumentShape.nativeSuggestions(EntityArgument.player())
                                    .withLiteralAlternative("@sender");
                        }
                        if (type == ServerLevel.class) {
                            return BrigadierArgumentShape.nativeSuggestions(DimensionArgument.dimension())
                                    .withLiteralAlternative("@current");
                        }
                        return null;
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }
                }));
        this.opLevel = opLevel;
        registerMagicSenderAdapter(opLevel);
    }

    /**
     * Initializes the command registry with the mod id and permission prefix.
     *
     * @param modId mod id for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerCore MagicUtils logger core
     */
    public static void initialize(String modId, String permissionPrefix, LoggerCore loggerCore) {
        createDefault(modId, permissionPrefix, loggerCore, 2);
    }

    /**
     * Initializes the command registry with an explicit op level.
     *
     * @param modId mod id for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerCore MagicUtils logger core
     * @param opLevel permission level to treat as OP
     */
    public static void initialize(String modId, String permissionPrefix, LoggerCore loggerCore, int opLevel) {
        createDefault(modId, permissionPrefix, loggerCore, opLevel);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param modId mod id for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerCore MagicUtils logger core
     * @return registry instance
     */
    public static CommandRegistry create(String modId, String permissionPrefix, LoggerCore loggerCore) {
        return create(modId, permissionPrefix, loggerCore, 2, false);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param modId mod id for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerCore MagicUtils logger core
     * @param opLevel permission level to treat as OP
     * @return registry instance
     */
    public static CommandRegistry create(String modId, String permissionPrefix, LoggerCore loggerCore, int opLevel) {
        return create(modId, permissionPrefix, loggerCore, opLevel, false);
    }

    /**
     * Creates a registry instance and sets it as default.
     *
     * @param modId mod id for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerCore MagicUtils logger core
     * @param opLevel permission level to treat as OP
     * @return registry instance
     */
    public static CommandRegistry createDefault(String modId, String permissionPrefix, LoggerCore loggerCore, int opLevel) {
        return create(modId, permissionPrefix, loggerCore, opLevel, true);
    }

    private static CommandRegistry create(String modId,
                                          String permissionPrefix,
                                          LoggerCore loggerCore,
                                          int opLevel,
                                          boolean makeDefault) {
        CommandRegistry registry = new CommandRegistry(modId, permissionPrefix, loggerCore, opLevel);
        REGISTRIES.put(registryKey(modId), registry);
        if (makeDefault || defaultRegistry == null) {
            defaultRegistry = registry;
        }
        return registry;
    }

    /**
     * Removes the registry entry for a mod.
     *
     * @param modId mod id
     */
    public static void shutdown(String modId) {
        if (modId == null) {
            return;
        }
        CommandRegistry registry = REGISTRIES.remove(registryKey(modId));
        if (registry != null && defaultRegistry == registry) {
            defaultRegistry = null;
        }
    }

    /**
     * Returns the registry instance by mod id.
     *
     * @param modId mod id
     * @return registry or null
     */
    public static CommandRegistry get(String modId) {
        if (modId == null) {
            return null;
        }
        return REGISTRIES.get(registryKey(modId));
    }

    /**
     * Registers multiple commands at once for a mod.
     *
     * @param modId mod id
     * @param dispatcher dispatcher to register on
     * @param commands commands to register
     */
    public static void registerAll(String modId, CommandDispatcher<CommandSourceStack> dispatcher,
                                   MagicCommand... commands) {
        require(modId).registerAllCommands(dispatcher, commands);
    }

    /**
     * Registers multiple builder-defined commands at once for a mod.
     *
     * @param modId mod id
     * @param dispatcher dispatcher to register on
     * @param specs command specs to register
     */
    public static void registerAll(String modId, CommandDispatcher<CommandSourceStack> dispatcher,
                                   CommandSpec<?>... specs) {
        require(modId).registerAllSpecs(dispatcher, specs);
    }

    /**
     * Registers a single command for a mod.
     *
     * @param modId mod id
     * @param dispatcher dispatcher to register on
     * @param command command to register
     */
    public static void register(String modId, CommandDispatcher<CommandSourceStack> dispatcher,
                                MagicCommand command) {
        require(modId).registerCommand(dispatcher, command);
    }

    /**
     * Registers a single builder-defined command for a mod.
     *
     * @param modId mod id
     * @param dispatcher dispatcher to register on
     * @param spec command spec to register
     */
    public static void register(String modId, CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandSpec<?> spec) {
        require(modId).registerSpec(dispatcher, spec);
    }

    /**
     * Returns the default command manager.
     *
     * @return command manager or null
     */
    public static CommandManager<CommandSourceStack> getCommandManager() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.commandManager() : null;
    }

    /**
     * Returns the command manager for a mod.
     *
     * @param modId mod id
     * @return command manager or null
     */
    public static CommandManager<CommandSourceStack> getCommandManager(String modId) {
        CommandRegistry registry = get(modId);
        return registry != null ? registry.commandManager() : null;
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
     * Returns true if the registry is initialized for the mod.
     *
     * @param modId mod id
     * @return true if initialized
     */
    public static boolean isInitialized(String modId) {
        CommandRegistry registry = get(modId);
        return registry != null && registry.initialized();
    }

    /**
     * Registers multiple commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param commands commands to register
     */
    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher, MagicCommand... commands) {
        requireDefault().registerAllCommands(dispatcher, commands);
    }

    /**
     * Registers multiple builder-defined commands at once.
     *
     * @param dispatcher dispatcher to register on
     * @param specs command specs to register
     */
    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher, CommandSpec<?>... specs) {
        requireDefault().registerAllSpecs(dispatcher, specs);
    }

    /**
     * Registers a single command.
     *
     * @param dispatcher dispatcher to register on
     * @param command command to register
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MagicCommand command) {
        requireDefault().registerCommand(dispatcher, command);
    }

    /**
     * Registers a single builder-defined command.
     *
     * @param dispatcher dispatcher to register on
     * @param spec command spec to register
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandSpec<?> spec) {
        requireDefault().registerSpec(dispatcher, spec);
    }

    private static void registerMagicSenderAdapter(int opLevel) {
        ADAPTER_OP_LEVEL.set(opLevel);
        if (!ADAPTER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MagicSenderAdapters.register("neoforge", new MagicSenderAdapter() {
            @Override
            public boolean supports(Object sender) {
                return sender instanceof CommandSourceStack || sender instanceof ServerPlayer;
            }

            @Override
            public MagicSender wrap(Object sender) {
                int level = resolveAdapterOpLevel();
                if (sender instanceof CommandSourceStack source) {
                    return NeoForgeCommandPlatform.wrapMagicSender(source, level);
                }
                if (sender instanceof ServerPlayer player) {
                    CommandSourceStack source = createCommandSource(player);
                    return source != null ? NeoForgeCommandPlatform.wrapMagicSender(source, level) : null;
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

    private static CommandRegistry require(String modId) {
        CommandRegistry registry = get(modId);
        if (registry == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        return registry;
    }

    private static String registryKey(String modId) {
        String name = modId != null ? modId.trim().toLowerCase(Locale.ROOT) : "";
        return name.isEmpty() ? "default" : name;
    }

    private static LoggerCore requireLoggerCore(LoggerCore loggerCore) {
        if (loggerCore == null) {
            throw new IllegalArgumentException("LoggerCore instance is required");
        }
        return loggerCore;
    }

    private static CommandSourceStack createCommandSource(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            Method method = player.getClass().getMethod("createCommandSourceStack");
            Object res = method.invoke(player);
            return res instanceof CommandSourceStack ? (CommandSourceStack) res : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isServerThread(CommandSourceStack source) {
        if (source == null || source.getServer() == null) {
            return false;
        }
        try {
            Method method = source.getServer().getClass().getMethod("isSameThread");
            Object res = method.invoke(source.getServer());
            return res instanceof Boolean && (Boolean) res;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
