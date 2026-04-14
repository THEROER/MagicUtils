package dev.ua.theroer.magicutils.commands.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.Option;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.commands.CommandArgument;
import dev.ua.theroer.magicutils.commands.CommandPlatform;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.CommandThreading;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;
import dev.ua.theroer.magicutils.commands.SenderMismatchException;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrigadierCommandRegistryTest {

    @Test
    void registerBuildsStructuredTreeForDirectArgsAndNestedSubcommands() {
        try (Harness harness = new Harness()) {
            BrigadierCommandRegistry<TestSender> registry = harness.registry();
            CommandDispatcher<TestSender> dispatcher = new CommandDispatcher<>();

            registry.registerCommand(dispatcher, new DemoCommand());

            CommandNode<TestSender> demo = dispatcher.getRoot().getChild("demo");
            assertNotNull(demo);
            assertNotNull(demo.getChild("target"));
            assertNotNull(demo.getChild("give"));
            assertNotNull(demo.getChild("give").getChild("effect"));
            assertNotNull(demo.getChild("give").getChild("effect").getChild("seconds"));
            assertNotNull(demo.getChild("admin"));
            assertNotNull(demo.getChild("admin").getChild("ban"));
            assertNotNull(demo.getChild("admin").getChild("block"));
            assertNull(demo.getChild("args"));
        }
    }

    @Test
    void registerBuildsFlagOptionBranchesWithoutLegacyFallback() {
        try (Harness harness = new Harness()) {
            BrigadierCommandRegistry<TestSender> registry = harness.registry();
            CommandDispatcher<TestSender> dispatcher = new CommandDispatcher<>();

            registry.registerCommand(dispatcher, new FlagOptionCommand());

            CommandNode<TestSender> opts = dispatcher.getRoot().getChild("flags");
            assertNotNull(opts);
            assertNotNull(opts.getChild("target"));
            assertNotNull(opts.getChild("--force"));
            assertNull(opts.getChild("args"));
            assertNotNull(opts.getChild("target").getChild("--force"));
        }
    }

    @Test
    void registerKeepsLegacyFallbackForValueOptionsWhileAddingStrictBranches() {
        try (Harness harness = new Harness()) {
            BrigadierCommandRegistry<TestSender> registry = harness.registry();
            CommandDispatcher<TestSender> dispatcher = new CommandDispatcher<>();

            registry.registerCommand(dispatcher, new ValueOptionCommand());

            CommandNode<TestSender> opts = dispatcher.getRoot().getChild("opts");
            assertNotNull(opts);
            assertNotNull(opts.getChild("target"));
            assertNotNull(opts.getChild("--amount"));
            assertNotNull(opts.getChild("-a"));
            assertNotNull(opts.getChild("args"));
            assertNotNull(opts.getChild("--amount").getChild("amount"));
        }
    }

    @Test
    void registerKeepsLiteralAlternativesAlongsideNativeArgumentNodes() {
        try (Harness harness = new Harness(registry -> registry.register(new BrigadierCommandRegistry.BrigadierArgumentResolver<>() {
            @Override
            public BrigadierCommandRegistry.BrigadierArgumentShape resolve(CommandArgument argument) {
                if (argument != null && "target".equals(argument.getName())) {
                    return BrigadierCommandRegistry.BrigadierArgumentShape.nativeSuggestions(StringArgumentType.word())
                            .withLiteralAlternative("@self");
                }
                return null;
            }

            @Override
            public int priority() {
                return 100;
            }
        }))) {
            CommandDispatcher<TestSender> dispatcher = new CommandDispatcher<>();

            harness.registry().registerCommand(dispatcher, new LiteralAliasCommand());

            CommandNode<TestSender> alias = dispatcher.getRoot().getChild("aliasdemo");
            assertNotNull(alias);
            assertNotNull(alias.getChild("@self"));
            ArgumentCommandNode<?, ?> target = assertInstanceOf(ArgumentCommandNode.class, alias.getChild("target"));
            assertInstanceOf(StringArgumentType.class, target.getType());
            assertNull(target.getCustomSuggestions());
        }
    }

    @CommandInfo(name = "demo", threading = CommandThreading.MAIN)
    private static final class DemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("target") String target,
                                     @OptionalArgument @ParamName("seconds") Integer seconds) {
            return CommandResult.success(target + ":" + seconds);
        }

        @SubCommand(name = "give")
        public CommandResult give(@ParamName("effect") String effect,
                                  @ParamName("seconds") Integer seconds) {
            return CommandResult.success(effect + ":" + seconds);
        }

        @SubCommand(name = "ban", path = {"admin"}, aliases = {"block"})
        public CommandResult ban(@ParamName("player") String player) {
            return CommandResult.success(player);
        }
    }

    @CommandInfo(name = "flags")
    private static final class FlagOptionCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@Option(longNames = {"force"}, flag = true) boolean force,
                                     @ParamName("target") String target) {
            return CommandResult.success(target + ":" + force);
        }
    }

    @CommandInfo(name = "opts")
    private static final class ValueOptionCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@Option(shortNames = {"a"}, longNames = {"amount"}) Integer amount,
                                     @ParamName("target") String target) {
            return CommandResult.success(target + ":" + amount);
        }
    }

    @CommandInfo(name = "aliasdemo")
    private static final class LiteralAliasCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("target") String target) {
            return CommandResult.success(target);
        }
    }

    private static final class Harness implements AutoCloseable {
        private final TestPlatform platform = new TestPlatform();
        private final ConfigManager configManager = new ConfigManager(platform);
        private final LoggerCore loggerCore = new LoggerCore(platform, configManager, this, "BrigadierRegistryTest");
        private final BrigadierCommandRegistry<TestSender> registry;

        private Harness() {
            this(null);
        }

        private Harness(Consumer<BrigadierCommandRegistry.BrigadierArgumentRegistry<TestSender>> brigadierRegistrar) {
            this.registry = new BrigadierCommandRegistry<>(
                    "testmod",
                    "",
                    loggerCore,
                    new TestCommandPlatform(),
                    null,
                    null,
                    null,
                    null,
                    brigadierRegistrar
            );
        }

        private BrigadierCommandRegistry<TestSender> registry() {
            return registry;
        }

        @Override
        public void close() {
            configManager.shutdown();
        }
    }

    private static final class TestCommandPlatform implements CommandPlatform<TestSender> {
        @Override
        public Class<?> senderType() {
            return TestSender.class;
        }

        @Override
        public String getName(TestSender sender) {
            return sender.name;
        }

        @Override
        public boolean hasPermission(TestSender sender, String permission, MagicPermissionDefault defaultValue) {
            return sender.permissions.contains(permission) || permission == null || permission.isEmpty();
        }

        @Override
        public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        }

        @Override
        public Object resolveSenderArgument(TestSender sender, CommandArgument argument) {
            throw new SenderMismatchException("sender arguments are not used in this test");
        }
    }

    private static final class TestSender {
        private final String name = "tester";
        private final Set<String> permissions = new HashSet<>();
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
        @Override
        public Path configDir() {
            return Path.of(System.getProperty("java.io.tmpdir"));
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
            return true;
        }

        @Override
        public TaskScheduler scheduler() {
            return null;
        }

        @Override
        public String defaultConfigExtension() {
            return "json";
        }
    }

    private enum NoOpAudience implements Audience {
        INSTANCE;

        @Override
        public void send(Component component) {
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
}
