package dev.ua.theroer.magicutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.brigadier.BrigadierCommandRegistry;
import dev.ua.theroer.magicutils.commands.parsers.PlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.WorldTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import dev.ua.theroer.magicutils.platform.fabric.FabricCommandAudience;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles registration and initialization of commands in Fabric.
 */
public final class CommandRegistry extends BrigadierCommandRegistry<ServerCommandSource> {
    private static final Map<String, CommandRegistry> REGISTRIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean ADAPTER_REGISTERED = new AtomicBoolean();
    private static final AtomicInteger ADAPTER_OP_LEVEL = new AtomicInteger(2);
    private static volatile CommandRegistry defaultRegistry;
    private final int opLevel;

    private CommandRegistry(String modName, String permissionPrefix, Logger loggerInstance, int opLevel) {
        super(modName,
                permissionPrefix,
                requireLoggerCore(loggerInstance),
                new FabricCommandPlatform(opLevel),
                registry -> {
                    PrefixedLogger parserLogger = loggerInstance.withPrefix("Commands", "[Commands]");
                    registry.register(new PlayerTypeParser(parserLogger));
                    registry.register(new WorldTypeParser(parserLogger));
                },
                (source, error) -> new FabricCommandAudience(source, false,
                        error ? FabricCommandAudience.Mode.ERROR : FabricCommandAudience.Mode.FEEDBACK),
                (source, task) -> {
                    if (task == null) {
                        return;
                    }
                    if (source == null || source.getServer() == null || source.getServer().isOnThread()) {
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
                        if (type == ServerPlayerEntity.class) {
                            return BrigadierArgumentShape.nativeSuggestions(EntityArgumentType.player())
                                    .withLiteralAlternative("@sender");
                        }
                        if (type == ServerWorld.class) {
                            return BrigadierArgumentShape.nativeSuggestions(DimensionArgumentType.dimension())
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
     * Initializes the command registry with the mod name and permission prefix.
     *
     * @param modName mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance magicutils logger
     */
    public static void initialize(String modName, String permissionPrefix, Logger loggerInstance) {
        createDefault(modName, permissionPrefix, loggerInstance, 2);
    }

    /**
     * Initializes the command registry with an explicit op level.
     *
     * @param modName mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance magicutils logger
     * @param opLevel permission level to treat as OP
     */
    public static void initialize(String modName, String permissionPrefix, Logger loggerInstance, int opLevel) {
        createDefault(modName, permissionPrefix, loggerInstance, opLevel);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param modName mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance magicutils logger
     * @return registry instance
     */
    public static CommandRegistry create(String modName, String permissionPrefix, Logger loggerInstance) {
        return create(modName, permissionPrefix, loggerInstance, 2, false);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param modName mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance magicutils logger
     * @param opLevel permission level to treat as OP
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
     * @param modName mod name for namespaced commands
     * @param permissionPrefix prefix for permissions
     * @param loggerInstance magicutils logger
     * @param opLevel permission level to treat as OP
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
     * Removes the registry entry for a mod.
     *
     * @param modName mod name
     */
    public static void shutdown(String modName) {
        if (modName == null) {
            return;
        }
        CommandRegistry registry = REGISTRIES.remove(registryKey(modName));
        if (registry != null && defaultRegistry == registry) {
            defaultRegistry = null;
        }
    }

    /**
     * Clears all registry references.
     */
    public static void clearRegistries() {
        REGISTRIES.clear();
        defaultRegistry = null;
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
     * Registers multiple commands at once for a mod.
     *
     * @param modName mod name
     * @param dispatcher dispatcher to register on
     * @param commands commands to register
     */
    public static void registerAll(String modName, CommandDispatcher<ServerCommandSource> dispatcher,
                                   MagicCommand... commands) {
        require(modName).registerAllCommands(dispatcher, commands);
    }

    /**
     * Registers multiple builder-defined commands at once for a mod.
     *
     * @param modName mod name
     * @param dispatcher dispatcher to register on
     * @param specs command specs to register
     */
    public static void registerAll(String modName, CommandDispatcher<ServerCommandSource> dispatcher,
                                   CommandSpec<?>... specs) {
        require(modName).registerAllSpecs(dispatcher, specs);
    }

    /**
     * Registers a single command for a mod.
     *
     * @param modName mod name
     * @param dispatcher dispatcher to register on
     * @param command command to register
     */
    public static void register(String modName, CommandDispatcher<ServerCommandSource> dispatcher,
                                MagicCommand command) {
        require(modName).registerCommand(dispatcher, command);
    }

    /**
     * Registers a single builder-defined command for a mod.
     *
     * @param modName mod name
     * @param dispatcher dispatcher to register on
     * @param spec command spec to register
     */
    public static void register(String modName, CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandSpec<?> spec) {
        require(modName).registerSpec(dispatcher, spec);
    }

    /**
     * Returns the default command manager.
     *
     * @return command manager or null
     */
    public static CommandManager<ServerCommandSource> getCommandManager() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.commandManager() : null;
    }

    /**
     * Returns the command manager for a mod.
     *
     * @param modName mod name
     * @return command manager or null
     */
    public static CommandManager<ServerCommandSource> getCommandManager(String modName) {
        CommandRegistry registry = get(modName);
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
     * @param modName mod name
     * @return true if initialized
     */
    public static boolean isInitialized(String modName) {
        CommandRegistry registry = get(modName);
        return registry != null && registry.initialized();
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

    private static CommandRegistry require(String modName) {
        CommandRegistry registry = get(modName);
        if (registry == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        return registry;
    }

    private static String registryKey(String modName) {
        String name = modName != null ? modName.trim().toLowerCase(Locale.ROOT) : "";
        return name.isEmpty() ? "default" : name;
    }

    private static LoggerCore requireLoggerCore(Logger loggerInstance) {
        if (loggerInstance == null) {
            throw new IllegalArgumentException("Logger instance is required");
        }
        return loggerInstance.getCore();
    }
}
