package dev.ua.theroer.magicutils.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsSupportTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void installRegistersComponentsAndBuiltInChecks() {
        TestPlatform platform = new TestPlatform(tempDir.resolve("runtime"));
        ConfigManager configManager = new ConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "DiagnosticsSupportTest");
        MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build();

        DiagnosticsService service = DiagnosticsSupport.install(runtime);

        assertSame(service, runtime.requireComponent(DiagnosticsService.class));
        assertSame(service.registry(), runtime.requireComponent(DiagnosticRegistry.class));
        assertNotNull(runtime.requireNamedComponent("diagnostics.service", DiagnosticsService.class));
        assertTrue(service.registry().checks().size() >= 10);
        assertTrue(service.registry().find("magicutils.commands.registry.present").isPresent());
        assertEquals(
                "magicutils.commands",
                service.registry().find("magicutils.commands.registry.present").orElseThrow().suite()
        );

        runtime.close();
        configManager.shutdown();
    }

    @Test
    void runAllProducesSummaryAndJsonExport() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir.resolve("report"));
        Files.createDirectories(platform.configDir());
        ConfigManager configManager = new ConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "DiagnosticsSupportTest");
        MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build();

        DiagnosticsService service = DiagnosticsSupport.install(runtime, registry -> registry.register(new DiagnosticCheck() {
            @Override
            public String id() {
                return "custom.success";
            }

            @Override
            public String suite() {
                return "custom";
            }

            @Override
            public String description() {
                return "Custom success check";
            }

            @Override
            public DiagnosticSeverity severity() {
                return DiagnosticSeverity.INFO;
            }

            @Override
            public java.util.concurrent.CompletionStage<DiagnosticResult> run(DiagnosticContext context) {
                return CompletableFuture.completedFuture(DiagnosticResult.ok(
                        id(),
                        suite(),
                        severity(),
                        "Custom check passed",
                        Map.of("verbose", context.verbose())
                ));
            }
        }));

        DiagnosticReport report = service.runAll(new DiagnosticRunRequest(
                DiagnosticMode.STANDARD,
                true,
                Duration.ofSeconds(2),
                check -> check.id().startsWith("magicutils.runtime.") || check.id().startsWith("custom.")
        ));

        assertEquals(6, report.results().size());
        assertEquals(5, report.okCount());
        assertEquals(1, report.skippedCount());
        Path exported = service.exportJson(report);
        assertTrue(Files.exists(exported));
        JsonNode json = OBJECT_MAPPER.readTree(Files.readString(exported));
        assertEquals("STANDARD", json.get("technical").get("request").get("mode").asText());
        assertEquals(
                TestPlatform.class.getName(),
                json.get("technical").get("runtime").get("platform").get("providerClass").asText()
        );
        assertTrue(
                json.get("technical").get("runtime").get("components").get("typed")
                        .has("dev.ua.theroer.magicutils.platform.Platform")
        );
        assertTrue(
                json.get("technical").get("runtime").get("components").get("named")
                        .has("diagnostics.service")
        );
        assertEquals(6, json.get("technical").get("selection").get("selectedCheckCount").asInt());
        assertEquals(6, json.get("technical").get("checks").size());
        assertTrue(DiagnosticReports.summaryLine(report).contains("<green>"));
        assertTrue(DiagnosticReports.renderText(report).getFirst().contains("<aqua>Diagnostics</aqua>"));

        runtime.close();
        configManager.shutdown();
    }

    @Test
    void timeoutTurnsIntoFailure() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir.resolve("timeout"));
        ConfigManager configManager = new ConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "DiagnosticsSupportTest");
        MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build();

        DiagnosticsService service = DiagnosticsSupport.install(runtime, registry -> registry.register(new DiagnosticCheck() {
            @Override
            public String id() {
                return "custom.timeout";
            }

            @Override
            public String suite() {
                return "custom";
            }

            @Override
            public String description() {
                return "Never completes";
            }

            @Override
            public DiagnosticSeverity severity() {
                return DiagnosticSeverity.CRITICAL;
            }

            @Override
            public java.util.concurrent.CompletionStage<DiagnosticResult> run(DiagnosticContext context) {
                return new CompletableFuture<>();
            }
        }));

        DiagnosticReport report = service.runChecks(List.of("custom.timeout"),
                new DiagnosticRunRequest(DiagnosticMode.SAFE, false, Duration.ofMillis(50), null));

        assertEquals(1, report.failCount());
        assertEquals(DiagnosticStatus.FAIL, report.results().getFirst().status());
        assertTrue(report.results().getFirst().message().contains("timed out"));
        Path exported = service.exportJson(report);
        JsonNode json = OBJECT_MAPPER.readTree(Files.readString(exported));
        JsonNode error = json.get("results").get(0).get("error");
        assertEquals("java.util.concurrent.TimeoutException", error.get("class").asText());
        assertTrue(error.get("stackTrace").isArray());

        runtime.close();
        configManager.shutdown();
    }

    @Test
    void registrySupportsOverrideLookupAndUnregister() {
        DefaultDiagnosticRegistry registry = new DefaultDiagnosticRegistry();
        DiagnosticCheck first = simpleCheck("sample.one", "suite.alpha");
        DiagnosticCheck second = simpleCheck("sample.two", "suite.alpha");
        DiagnosticCheck replacement = simpleCheck("sample.one", "suite.beta");

        registry.register(first);
        registry.register(second);
        registry.register(replacement);

        assertEquals(List.of(replacement, second), registry.checks());
        assertSame(replacement, registry.find("sample.one").orElseThrow());
        assertEquals(List.of(replacement), registry.checks("suite.beta"));
        assertSame(second, registry.unregister("sample.two").orElseThrow());
        assertTrue(registry.find("sample.two").isEmpty());
    }

    @Test
    void namespacedRegistryPrefixesIdsAndSuites() {
        DefaultDiagnosticRegistry registry = new DefaultDiagnosticRegistry();
        DiagnosticRegistry magicutils = registry.namespaced("magicutils");

        magicutils.register(simpleCheck("commands.registry.present", "commands"));

        DiagnosticCheck stored = registry.find("magicutils.commands.registry.present").orElseThrow();
        assertEquals("magicutils.commands.registry.present", stored.id());
        assertEquals("magicutils.commands", stored.suite());
        assertEquals(List.of(stored), magicutils.checks("commands"));
    }

    @Test
    void runSuiteAndRunChecksRespectFiltering() {
        TestPlatform platform = new TestPlatform(tempDir.resolve("filtering"));
        ConfigManager configManager = new ConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "DiagnosticsSupportTest");
        MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build();

        DiagnosticsService service = DiagnosticsSupport.install(runtime, registry -> {
            registry.register(simpleCheck("custom.alpha", "suite.alpha"));
            registry.register(simpleCheck("custom.beta", "suite.beta"));
        });

        DiagnosticReport suiteReport = service.runSuite("suite.alpha",
                new DiagnosticRunRequest(DiagnosticMode.SAFE, false, Duration.ofSeconds(1),
                        check -> check.id().startsWith("custom.")));
        DiagnosticReport checksReport = service.runChecks(List.of("custom.beta"), DiagnosticRunRequest.safe());

        assertEquals(1, suiteReport.results().size());
        assertEquals("custom.alpha", suiteReport.results().getFirst().checkId());
        assertEquals(1, checksReport.results().size());
        assertEquals("custom.beta", checksReport.results().getFirst().checkId());

        runtime.close();
        configManager.shutdown();
    }

    @Test
    void renderTextEscapesDynamicContentAndAddsStatusColors() {
        DiagnosticReport report = new DiagnosticReport(
                "Magic<Utils>",
                DiagnosticMode.SAFE,
                Instant.EPOCH,
                Duration.ofMillis(12),
                List.of(
                        DiagnosticResult.fail(
                                "check<danger>",
                                "suite",
                                DiagnosticSeverity.CRITICAL,
                                "Failure with <red>tag</red>",
                                Map.of(),
                                null
                        )
                )
        );

        List<String> lines = DiagnosticReports.renderVerboseText(report);

        assertTrue(lines.getFirst().contains("<aqua>Diagnostics</aqua>"));
        assertTrue(lines.get(1).contains("<red>[FAIL]</red>"));
        assertTrue(lines.get(1).contains("Magic\\<Utils\\>") || lines.getFirst().contains("Magic\\<Utils\\>"));
        assertTrue(lines.get(1).contains("check\\<danger\\>"));
        assertTrue(lines.get(1).contains("Failure with \\<red\\>tag\\</red\\>"));
    }

    @Test
    void compactRenderShowsOnlyNonOkChecksAndHints() {
        DiagnosticReport report = new DiagnosticReport(
                "MagicUtils",
                DiagnosticMode.STANDARD,
                Instant.EPOCH,
                Duration.ofMillis(42),
                List.of(
                        DiagnosticResult.ok(
                                "magicutils.runtime.platform.present",
                                "magicutils.runtime",
                                DiagnosticSeverity.CRITICAL,
                                "Platform is available",
                                Map.of()
                        ),
                        DiagnosticResult.warn(
                                "leavepulse.whitelist.service",
                                "leavepulse",
                                DiagnosticSeverity.WARNING,
                                "Whitelist service is configured, but gateway is not ready",
                                Map.of("actionHint", "Configure gateway.token and wait for Gateway ready.")
                        ),
                        DiagnosticResult.skipped(
                                "leavepulse.gateway.token",
                                "leavepulse",
                                DiagnosticSeverity.INFO,
                                "Gateway token is not configured",
                                Map.of("missingRequirements", List.of("gateway.token"))
                        )
                )
        );

        List<String> lines = DiagnosticReports.renderText(report);

        assertTrue(lines.getFirst().contains("<aqua>Diagnostics</aqua>"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Showing non-OK checks only")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Suites:")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("whitelist.service")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("gateway.token")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Configure gateway.token and wait for Gateway ready.")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Missing: gateway.token")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("platform.present")));
    }

    private static DiagnosticCheck simpleCheck(String id, String suite) {
        return new DiagnosticCheck() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String suite() {
                return suite;
            }

            @Override
            public String description() {
                return id + " description";
            }

            @Override
            public DiagnosticSeverity severity() {
                return DiagnosticSeverity.INFO;
            }

            @Override
            public java.util.concurrent.CompletionStage<DiagnosticResult> run(DiagnosticContext context) {
                return CompletableFuture.completedFuture(DiagnosticResult.ok(
                        id,
                        suite,
                        severity(),
                        id + " ok",
                        Map.of()
                ));
            }
        };
    }

    private static final class TestPlatform implements Platform {
        private final Path configDir;
        private final PlatformLogger logger = new TestPlatformLogger();
        private final TaskScheduler scheduler = TaskSchedulers.create("diagnostics-test", null);

        private TestPlatform(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public Path configDir() {
            return configDir;
        }

        @Override
        public PlatformLogger logger() {
            return logger;
        }

        @Override
        public Audience console() {
            return null;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return List.of();
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
    }

    private static final class TestPlatformLogger implements PlatformLogger {
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
