package dev.ua.theroer.magicutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryIntegrationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupStatics() throws Exception {
        resetRegistryState();
    }

    @Test
    void registerAddsBaseAliasAndNamespacedLiteralsToDispatcher() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestMod", 4)) {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

            CommandRegistry.register(dispatcher, new DemoCommand());

            assertLiteralPresent(dispatcher, "demo");
            assertLiteralPresent(dispatcher, "alias");
            assertLiteralPresent(dispatcher, "testmod:demo");
            assertLiteralPresent(dispatcher, "testmod:alias");
            assertSame(harness.registry.commandManager(), CommandRegistry.getCommandManager());
            assertSame(harness.registry.commandManager(), CommandRegistry.getCommandManager("TESTMOD"));
        }
    }

    @Test
    void namedRegistryLookupIsCaseInsensitiveAndRequiresInitialization() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "FancyMod", 3)) {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            assertNotNull(harness.registry);

            CommandRegistry.register("FANCYMOD", dispatcher, new DemoCommand());

            assertTrue(CommandRegistry.isInitialized());
            assertTrue(CommandRegistry.isInitialized("fancymod"));
            assertTrue(CommandRegistry.isInitialized("FANCYMOD"));
            assertNotNull(CommandRegistry.get("fancymod"));
            assertNotNull(CommandRegistry.get("FANCYMOD"));
            assertLiteralPresent(dispatcher, "fancymod:demo");
        }

        assertFalse(CommandRegistry.isInitialized());
        assertNull(CommandRegistry.getCommandManager());
        assertThrows(IllegalStateException.class,
                () -> CommandRegistry.register(new CommandDispatcher<CommandSourceStack>(), new DemoCommand()));
    }

    @Test
    void createDefaultRegistersFabricAdapterAndTracksOpLevel() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "AdapterMod", 5)) {
            assertNotNull(harness.registry);
            assertTrue(hasRegisteredAdapter("fabric"));
            assertEquals(5, currentAdapterOpLevel());
        }
    }

    @Test
    void registerUsesNativeBrigadierShapeForPlayerArguments() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "NativeMod", 4)) {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            CommandRegistry registry = harness.registry;

            registry.registerCommand(dispatcher, new NativePlayerCommand());

            CommandNode<CommandSourceStack> nativeRoot = dispatcher.getRoot().getChild("nativeplayer");
            assertNotNull(nativeRoot);
            assertNotNull(nativeRoot.getChild("@sender"));

            ArgumentCommandNode<?, ?> player = assertInstanceOf(
                    ArgumentCommandNode.class,
                    nativeRoot.getChild("player")
            );
            assertInstanceOf(EntityArgument.class, player.getType());
            assertNull(player.getCustomSuggestions());
        }
    }

    @Test
    void opOnlyCommandsUseModernPermissionSources() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "PermissionMod", 2)) {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            assertNotNull(harness.registry);

            CommandRegistry.register(dispatcher, new RestrictedCommand());

            CommandNode<CommandSourceStack> restricted = dispatcher.getRoot().getChild("restricted");
            assertNotNull(restricted);
            assertTrue(restricted.canUse(commandSourceWithPermissionLevel(2)));
            assertFalse(restricted.canUse(commandSourceWithPermissionLevel(0)));
        }
    }

    @CommandInfo(name = "demo", aliases = {"alias"})
    private static final class DemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }
    }

    @CommandInfo(name = "nativeplayer")
    private static final class NativePlayerCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("player") ServerPlayer player) {
            return CommandResult.success(player.getName().getString());
        }
    }

    @CommandInfo(name = "restricted")
    private static final class RestrictedCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }
    }

    private static final class TestHarness implements AutoCloseable {
        private final TestPlatform platform;
        private final ConfigManager configManager;
        private final LoggerCore loggerCore;
        private final Logger logger;
        private final CommandRegistry registry;
        private boolean closed;

        private TestHarness(Path tempDir, String modName, int opLevel) throws Exception {
            resetRegistryState();
            this.platform = new TestPlatform(tempDir);
            this.configManager = new ConfigManager(platform);
            this.loggerCore = new LoggerCore(platform, configManager, this, modName);
            this.logger = fabricateLogger(loggerCore);
            this.registry = CommandRegistry.createDefault(modName, "", logger, opLevel);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            configManager.shutdown();
            platform.shutdown();
            try {
                resetRegistryState();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void assertLiteralPresent(CommandDispatcher<CommandSourceStack> dispatcher, String label) {
        assertNotNull(dispatcher.getRoot().getChild(label), "Missing literal: " + label);
    }

    private static boolean hasRegisteredAdapter(String id) throws Exception {
        Field adaptersField = MagicSenderAdapters.class.getDeclaredField("ADAPTERS");
        adaptersField.setAccessible(true);
        Map<?, ?> adapters = (Map<?, ?>) adaptersField.get(null);
        return adapters.containsKey(id);
    }

    private static int currentAdapterOpLevel() throws Exception {
        Field field = CommandRegistry.class.getDeclaredField("ADAPTER_OP_LEVEL");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(null)).get();
    }

    private static CommandSourceStack commandSourceWithPermissionLevel(int permissionLevel) throws Exception {
        sun.misc.Unsafe unsafe = unsafe();
        CommandSourceStack source = (CommandSourceStack) unsafe.allocateInstance(CommandSourceStack.class);
        if (putPermissionSetIfPresent(unsafe, source, permissionLevel)) {
            return source;
        }
        if (putIntPermissionLevelIfPresent(unsafe, source, permissionLevel)) {
            return source;
        }
        throw new IllegalStateException("Unsupported CommandSourceStack permission model");
    }

    private static boolean putPermissionSetIfPresent(
            sun.misc.Unsafe unsafe,
            CommandSourceStack source,
            int permissionLevel
    ) throws Exception {
        Object permissionSet = permissionSet(permissionLevel);
        if (permissionSet == null) {
            return false;
        }
        Field field = findFieldByTypeName(CommandSourceStack.class, "net.minecraft.server.permissions.PermissionSet");
        if (field == null) {
            return false;
        }
        putObject(unsafe, source, field, permissionSet);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static boolean putIntPermissionLevelIfPresent(
            sun.misc.Unsafe unsafe,
            CommandSourceStack source,
            int permissionLevel
    ) {
        Field field = findFieldByType(CommandSourceStack.class, int.class, "permissionLevel");
        if (field == null) {
            return false;
        }
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putInt(source, offset, permissionLevel);
        return true;
    }

    private static Object permissionSet(int permissionLevel) throws Exception {
        try {
            Class<?> permissionSet = Class.forName("net.minecraft.server.permissions.PermissionSet");
            String fieldName = permissionLevel > 0 ? "ALL_PERMISSIONS" : "NO_PERMISSIONS";
            return permissionSet.getField(fieldName).get(null);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Field findFieldByType(Class<?> owner, Class<?> type, String preferredName) {
        Field fallback = null;
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() != type) {
                continue;
            }
            if (preferredName != null && preferredName.equals(field.getName())) {
                return field;
            }
            if (fallback == null) {
                fallback = field;
            }
        }
        return fallback;
    }

    private static Field findFieldByTypeName(Class<?> owner, String typeName) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType().getName().equals(typeName)) {
                return field;
            }
        }
        return null;
    }

    private static void resetRegistryState() throws Exception {
        MagicSenderAdapters.clear();

        Field registriesField = CommandRegistry.class.getDeclaredField("REGISTRIES");
        registriesField.setAccessible(true);
        ((Map<?, ?>) registriesField.get(null)).clear();

        Field defaultRegistryField = CommandRegistry.class.getDeclaredField("defaultRegistry");
        defaultRegistryField.setAccessible(true);
        defaultRegistryField.set(null, null);

        Field adapterRegisteredField = CommandRegistry.class.getDeclaredField("ADAPTER_REGISTERED");
        adapterRegisteredField.setAccessible(true);
        ((AtomicBoolean) adapterRegisteredField.get(null)).set(false);

        Field adapterOpLevelField = CommandRegistry.class.getDeclaredField("ADAPTER_OP_LEVEL");
        adapterOpLevelField.setAccessible(true);
        ((AtomicInteger) adapterOpLevelField.get(null)).set(2);
    }

    private static Logger fabricateLogger(LoggerCore core) throws Exception {
        sun.misc.Unsafe unsafe = unsafe();
        Logger logger = (Logger) unsafe.allocateInstance(Logger.class);
        putObject(unsafe, Logger.class, logger, "core", core);
        putObject(unsafe, Logger.class, logger, "prefixedLoggers", new HashMap<>());
        return logger;
    }

    @SuppressWarnings("deprecation")
    private static void putObject(sun.misc.Unsafe unsafe, Object target, Field field, Object value) {
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(target, offset, value);
    }

    @SuppressWarnings("deprecation")
    private static void putObject(sun.misc.Unsafe unsafe,
                                  Class<?> type,
                                  Object target,
                                  String fieldName,
                                  Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(target, offset, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
        private final Path configDir;
        private final TaskScheduler scheduler = new DirectTaskScheduler();

        private TestPlatform(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public Path configDir() {
            return configDir;
        }

        @Override
        public PlatformLogger logger() {
            return NoOpLogger.INSTANCE;
        }

        @Override
        public Audience console() {
            return NoOpAudience.INSTANCE;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return Collections.emptyList();
        }

        @Override
        public void runOnMain(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public boolean isMainThread() {
            return false;
        }

        @Override
        public TaskScheduler scheduler() {
            return scheduler;
        }

        @Override
        public String defaultConfigExtension() {
            return "json";
        }

        private void shutdown() {
            scheduler.shutdown();
        }
    }

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "fabric-command-registry-test-timer");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public Executor cpu() {
            return directExecutor;
        }

        @Override
        public Executor io() {
            return directExecutor;
        }

        @Override
        public ScheduledExecutorService scheduler() {
            return timer;
        }

        @Override
        public void shutdown() {
            timer.shutdownNow();
        }
    }

    private enum NoOpLogger implements PlatformLogger {
        INSTANCE;

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }

    private enum NoOpAudience implements Audience {
        INSTANCE;

        @Override
        public void send(Component component) {
        }
    }
}
