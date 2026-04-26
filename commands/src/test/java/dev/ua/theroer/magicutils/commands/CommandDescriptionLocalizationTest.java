package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDescriptionLocalizationTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearMessages() {
        Messages.unregister("schema");
        Messages.setLanguageManager(null);
    }

    @Test
    void describeResolvesAtPrefixedDescriptionsUsingScopedLanguageManager() {
        try (LanguageHarness language = new LanguageHarness(tempDir.resolve("schema"))) {
            CommandManager<TestSender> manager = new CommandManager<>(
                    "",
                    "schema",
                    CommandLogger.noop(),
                    new TestCommandPlatform(),
                    TypeParserRegistry.createWithDefaults(CommandLogger.noop())
            );
            LocalizedDemoCommand command = new LocalizedDemoCommand();
            manager.register(command, LocalizedDemoCommand.class.getAnnotation(CommandInfo.class));

            ResolvedCommandSchema schema = manager.describe("demo");

            assertNotNull(schema);
            assertEquals("en", language.languageManager.getCurrentLanguage());
            assertEquals("English root description", schema.description());
            assertNotNull(schema.directAction());
            assertEquals("English root description", schema.directAction().description());

            ResolvedSubCommandNode status = schema.subCommands().children().stream()
                    .filter(node -> node.name().equals("status"))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(status.action());
            assertEquals("English sub description", status.action().description());
        }
    }

    @Test
    void helpBuildUsesAudienceLocalizedDescriptionsForAtPrefixedValues() {
        try (LanguageHarness language = new LanguageHarness(tempDir.resolve("schema"))) {
            CommandManager<TestSender> manager = new CommandManager<>(
                    "",
                    "schema",
                    CommandLogger.noop(),
                    new TestCommandPlatform(),
                    TypeParserRegistry.createWithDefaults(CommandLogger.noop())
            );
            LocalizedDemoCommand command = new LocalizedDemoCommand();
            manager.register(command, LocalizedDemoCommand.class.getAnnotation(CommandInfo.class));

            MagicSender sender = new TestMagicSender(new TestAudience(language.playerId, "Player"));

            HelpCommandSupport.HelpResult rootResult = HelpCommandSupport.build(
                    manager, "demo", null, "mhelp", null, sender);
            HelpCommandSupport.HelpResult subResult = HelpCommandSupport.build(
                    manager, "demo", "status", "mhelp", null, sender);

            assertTrue(rootResult.success());
            assertTrue(subResult.success());
            assertTrue(rootResult.lines().stream().anyMatch(line -> line.contains("Український опис команди")));
            assertTrue(rootResult.lines().stream().anyMatch(line -> line.contains("Опис:")));
            assertTrue(rootResult.lines().stream().anyMatch(line -> line.contains("Підкоманди:")));
            assertTrue(subResult.lines().stream().anyMatch(line -> line.contains("Український опис підкоманди")));
            assertTrue(subResult.lines().stream().anyMatch(line -> line.contains("Аргументи:")
                    || line.contains("Команда:")));
        }
    }

    @Test
    void describeResolvesImplicitDescriptionKeysWhenDescriptionsAreBlank() {
        try (LanguageHarness language = new LanguageHarness(tempDir.resolve("schema"))) {
            CommandManager<TestSender> manager = new CommandManager<>(
                    "",
                    "schema",
                    CommandLogger.noop(),
                    new TestCommandPlatform(),
                    TypeParserRegistry.createWithDefaults(CommandLogger.noop())
            );
            ImplicitLocalizedDemoCommand command = new ImplicitLocalizedDemoCommand();
            manager.register(command, ImplicitLocalizedDemoCommand.class.getAnnotation(CommandInfo.class));

            ResolvedCommandSchema schema = manager.describe("implicit");

            assertNotNull(schema);
            assertEquals("en", language.languageManager.getCurrentLanguage());
            assertEquals("English implicit root description", schema.description());
            assertNotNull(schema.directAction());
            assertEquals("English implicit root description", schema.directAction().description());

            ResolvedSubCommandNode status = schema.subCommands().children().stream()
                    .filter(node -> node.name().equals("status"))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(status.action());
            assertEquals("English implicit sub description", status.action().description());
        }
    }

    @Test
    void helpBuildUsesAudienceLocalizedDescriptionsForImplicitKeys() {
        try (LanguageHarness language = new LanguageHarness(tempDir.resolve("schema"))) {
            CommandManager<TestSender> manager = new CommandManager<>(
                    "",
                    "schema",
                    CommandLogger.noop(),
                    new TestCommandPlatform(),
                    TypeParserRegistry.createWithDefaults(CommandLogger.noop())
            );
            ImplicitLocalizedDemoCommand command = new ImplicitLocalizedDemoCommand();
            manager.register(command, ImplicitLocalizedDemoCommand.class.getAnnotation(CommandInfo.class));

            MagicSender sender = new TestMagicSender(new TestAudience(language.playerId, "Player"));

            HelpCommandSupport.HelpResult rootResult = HelpCommandSupport.build(
                    manager, "implicit", null, "mhelp", null, sender);
            HelpCommandSupport.HelpResult subResult = HelpCommandSupport.build(
                    manager, "implicit", "status", "mhelp", null, sender);

            assertTrue(rootResult.success());
            assertTrue(subResult.success());
            assertTrue(rootResult.lines().stream().anyMatch(line -> line.contains("Український неявний опис команди")));
            assertTrue(subResult.lines().stream().anyMatch(line -> line.contains("Український неявний опис підкоманди")));
        }
    }

    @Test
    void describeResolvesImplicitDescriptionKeysForBuilderCommands() {
        try (LanguageHarness language = new LanguageHarness(tempDir.resolve("schema"))) {
            CommandManager<TestSender> manager = new CommandManager<>(
                    "",
                    "schema",
                    CommandLogger.noop(),
                    new TestCommandPlatform(),
                    TypeParserRegistry.createWithDefaults(CommandLogger.noop())
            );
            MagicCommand command = MagicCommand.<TestSender>builder("builderdemo")
                    .execute(ctx -> CommandResult.success("ok"))
                    .subCommand(SubCommandSpec.<TestSender>builder("reload")
                            .execute(ctx -> CommandResult.success("ok"))
                            .build())
                    .build();
            manager.register(command, command.resolveInfo());

            ResolvedCommandSchema schema = manager.describe("builderdemo");

            assertNotNull(schema);
            assertEquals("en", language.languageManager.getCurrentLanguage());
            assertEquals("English builder root description", schema.description());
            assertNotNull(schema.directAction());
            assertEquals("English builder root description", schema.directAction().description());

            ResolvedSubCommandNode reload = schema.subCommands().children().stream()
                    .filter(node -> node.name().equals("reload"))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(reload.action());
            assertEquals("English builder sub description", reload.action().description());
        }
    }

    @CommandInfo(name = "demo", description = "@commands.demo.description")
    private static final class LocalizedDemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }

        @SuppressWarnings("unused")
        @SubCommand(name = "status", description = "@commands.demo.status.description")
        public CommandResult status() {
            return CommandResult.success("ok");
        }
    }

    @CommandInfo(name = "implicit")
    private static final class ImplicitLocalizedDemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }

        @SuppressWarnings("unused")
        @SubCommand(name = "status")
        public CommandResult status() {
            return CommandResult.success("ok");
        }
    }

    private static final class LanguageHarness implements AutoCloseable {
        private final TestPlatform platform;
        private final ConfigManager configManager;
        private final LanguageManager languageManager;
        private final UUID playerId = UUID.randomUUID();

        private LanguageHarness(Path configDir) {
            this.platform = new TestPlatform(configDir);
            this.configManager = new ConfigManager(platform);
            this.languageManager = new LanguageManager(platform, configManager);
            languageManager.registerTranslations(
                    "en",
                    java.util.Map.of(
                            "commands.demo.description", "English root description",
                            "commands.demo.status.description", "English sub description",
                            "commands.implicit.description", "English implicit root description",
                            "commands.implicit.status.description", "English implicit sub description",
                            "commands.builderdemo.description", "English builder root description",
                            "commands.builderdemo.reload.description", "English builder sub description"
                    )
            );
            languageManager.registerTranslations(
                    "uk",
                    java.util.Map.of(
                            "commands.demo.description", "Український опис команди",
                            "commands.demo.status.description", "Український опис підкоманди",
                            "commands.implicit.description", "Український неявний опис команди",
                            "commands.implicit.status.description", "Український неявний опис підкоманди",
                            "commands.builderdemo.description", "Український builder опис команди",
                            "commands.builderdemo.reload.description", "Український builder опис підкоманди"
                    )
            );
            languageManager.init("en");
            languageManager.setPlayerLanguage(playerId, "uk");
            Messages.register("schema", languageManager);
        }

        @Override
        public void close() {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static final class TestCommandPlatform implements CommandPlatform<TestSender> {
        @Override
        public Class<?> senderType() {
            return TestSender.class;
        }

        @Override
        public String getName(TestSender sender) {
            return sender.name();
        }

        @Override
        public boolean hasPermission(TestSender sender, String permission, MagicPermissionDefault defaultValue) {
            return true;
        }

        @Override
        public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        }

        @Override
        public Object resolveSenderArgument(TestSender sender, CommandArgument argument) {
            throw new SenderMismatchException("sender arguments are not used in this test");
        }
    }

    private record TestSender(String name) {
    }

    private record TestMagicSender(TestAudience audience) implements MagicSender {
        @Override
        public String name() {
            return audience.name();
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public Object handle() {
            return null;
        }
    }

    private record TestAudience(UUID id, String name) implements Audience {
        @Override
        public void send(Component component) {
        }
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
            return true;
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
            Thread thread = new Thread(r, "command-description-localization-test");
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
