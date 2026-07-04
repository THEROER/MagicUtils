package dev.ua.theroer.magicutils.diagnostics.testkit;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsSupport;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reusable JUnit harness that boots a self-contained MagicUtils runtime for running
 * diagnostics without a real Minecraft server.
 *
 * <p>Historically every consumer (for example {@code verified-plugin}) copied a fake
 * {@code Platform} plus the {@code ConfigManager} / {@code LoggerCore} / {@code MagicRuntime}
 * wiring into each test. This harness centralises that boilerplate: constructing it
 * stands up a {@link TestPlatform}, a {@link ConfigManager}, a {@link LoggerCore} and a
 * {@link MagicRuntime} configured with {@code manageConfigManager(false)} and
 * {@code autoRegisterShutdown(false)} so the test owns lifecycle explicitly.</p>
 *
 * <p>The harness is {@link AutoCloseable}; use it in a try-with-resources block so that
 * {@link #close()} tears the components down in the correct order
 * ({@code runtime} -&gt; {@code configManager} -&gt; {@code platform}).</p>
 *
 * <pre>{@code
 * try (DiagnosticsTestHarness harness = DiagnosticsTestHarness.create(tempDir)) {
 *     DiagnosticsService diagnostics = harness.installDiagnostics(registry ->
 *         MyDiagnosticsSupport.registryConfigurer(...).accept(registry));
 *     DiagnosticReport report = diagnostics.runAll(DiagnosticRunRequest.standard());
 *     assertTrue(report.isPublishable());
 * }
 * }</pre>
 */
public final class DiagnosticsTestHarness implements AutoCloseable {
    private static final String DEFAULT_LOGGER_NAME = "MagicUtilsDiagnosticsTestKit";

    private final TestPlatform platform;
    private final ConfigManager configManager;
    private final LoggerCore logger;
    private final MagicRuntime runtime;

    private DiagnosticsTestHarness(
            TestPlatform platform,
            ConfigManager configManager,
            LoggerCore logger,
            MagicRuntime runtime
    ) {
        this.platform = platform;
        this.configManager = configManager;
        this.logger = logger;
        this.runtime = runtime;
    }

    /**
     * Creates a harness rooted at the given config directory using the default logger name.
     *
     * @param configDir base directory for configs/lang files (typically a JUnit {@code @TempDir});
     *                  must not be {@code null}
     * @return a fully wired harness ready for diagnostics
     */
    public static DiagnosticsTestHarness create(Path configDir) {
        return create(configDir, DEFAULT_LOGGER_NAME);
    }

    /**
     * Creates a harness rooted at the given config directory with a custom logger name.
     *
     * @param configDir  base directory for configs/lang files (typically a JUnit
     *                   {@code @TempDir}); must not be {@code null}
     * @param loggerName plugin/mod name used for the {@link LoggerCore} prefix; must not be {@code null}
     * @return a fully wired harness ready for diagnostics
     */
    public static DiagnosticsTestHarness create(Path configDir, String loggerName) {
        Objects.requireNonNull(configDir, "configDir");
        Objects.requireNonNull(loggerName, "loggerName");
        TestPlatform platform = new TestPlatform(configDir);
        try {
            ConfigManager configManager = new ConfigManager(platform);
            LoggerCore logger = new LoggerCore(platform, configManager, platform, loggerName);
            MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                    .manageConfigManager(false)
                    .autoRegisterShutdown(false)
                    .build();
            return new DiagnosticsTestHarness(platform, configManager, logger, runtime);
        } catch (RuntimeException error) {
            platform.shutdown();
            throw error;
        }
    }

    /**
     * Returns the runtime container backing this harness.
     *
     * @return runtime instance
     */
    public MagicRuntime runtime() {
        return runtime;
    }

    /**
     * Returns the in-memory test platform.
     *
     * @return platform instance
     */
    public TestPlatform platform() {
        return platform;
    }

    /**
     * Returns the config manager owned by this harness.
     *
     * @return config manager
     */
    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * Returns the logger core owned by this harness.
     *
     * @return logger core
     */
    public LoggerCore logger() {
        return logger;
    }

    /**
     * Installs the default diagnostics service (with built-in MagicUtils checks) into
     * the runtime.
     *
     * @return installed diagnostics service
     */
    public DiagnosticsService installDiagnostics() {
        return DiagnosticsSupport.install(runtime);
    }

    /**
     * Installs the diagnostics service and lets the caller register custom checks,
     * mirroring {@link DiagnosticsSupport#install(MagicRuntime, Consumer)}.
     *
     * @param registryConfigurer optional configurer invoked with the diagnostic registry
     *                           after built-in checks are registered; may be {@code null}
     * @return installed diagnostics service
     */
    public DiagnosticsService installDiagnostics(@Nullable Consumer<DiagnosticRegistry> registryConfigurer) {
        return DiagnosticsSupport.install(runtime, registryConfigurer);
    }

    /**
     * Closes the harness, releasing all backing resources in the correct order:
     * runtime, then config manager, then platform (which stops the scheduler thread).
     *
     * <p>Idempotent with respect to the runtime and config manager; safe to call from
     * a try-with-resources block.</p>
     */
    @Override
    public void close() {
        runtime.close();
        configManager.shutdown();
        platform.shutdown();
    }
}
