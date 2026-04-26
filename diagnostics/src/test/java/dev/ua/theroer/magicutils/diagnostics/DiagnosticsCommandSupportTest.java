package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.commands.MagicSender;
import dev.ua.theroer.magicutils.commands.CommandThreading;
import dev.ua.theroer.magicutils.commands.SubCommandSpec;
import dev.ua.theroer.magicutils.platform.Audience;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsCommandSupportTest {
    @Test
    void diagnosticsSubCommandRunsAsync() {
        SubCommandSpec<Object> spec = DiagnosticsCommandSupport.createDiagnosticsSubCommand(
                null,
                () -> null
        );

        assertEquals(CommandThreading.ASYNC, spec.threading());
    }

    @Test
    void diagnosticsSubCommandsExposeNestedStructure() {
        List<SubCommandSpec<Object>> specs = DiagnosticsCommandSupport.createDiagnosticsSubCommands(
                null,
                () -> null
        );

        assertEquals(3, specs.size());
        assertEquals("diagnostics", specs.get(0).name());
        assertIterableEquals(List.of(), specs.get(0).path());
        assertEquals("export", specs.get(1).name());
        assertIterableEquals(List.of("diagnostics"), specs.get(1).path());
        assertEquals("suite", specs.get(2).name());
        assertIterableEquals(List.of("diagnostics"), specs.get(2).path());
        assertTrue(specs.stream().allMatch(spec -> spec.threading() == CommandThreading.ASYNC));
        assertEquals(DiagnosticSuiteName.class, specs.get(2).arguments().get(1).getType());
    }

    @Test
    void renderReportUsesCompactFormatForPlayerSender() {
        DiagnosticReport report = sampleReport();

        List<String> lines = DiagnosticsCommandSupport.renderReport(report, new TestSender(UUID.randomUUID()));

        assertTrue(lines.stream().anyMatch(line -> line.contains("Showing non-OK checks only")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("runtime.platform.present")));
    }

    @Test
    void renderReportUsesVerboseFormatForConsoleLikeSender() {
        DiagnosticReport report = sampleReport();

        List<String> lines = DiagnosticsCommandSupport.renderReport(report, new TestSender(null));

        assertTrue(lines.stream().noneMatch(line -> line.contains("Showing non-OK checks only")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("runtime.platform.present")));
    }

    private static DiagnosticReport sampleReport() {
        return new DiagnosticReport(
                "MagicUtils",
                DiagnosticMode.STANDARD,
                Instant.EPOCH,
                Duration.ofMillis(12),
                List.of(
                        DiagnosticResult.ok(
                                "magicutils.runtime.platform.present",
                                "magicutils.runtime",
                                DiagnosticSeverity.CRITICAL,
                                "Platform is available",
                                Map.of()
                        ),
                        DiagnosticResult.warn(
                                "leavepulse.gateway.client",
                                "leavepulse",
                                DiagnosticSeverity.WARNING,
                                "Gateway client is running, but the session is not ready yet",
                                Map.of()
                        )
                )
        );
    }

    private record TestSender(UUID id) implements MagicSender {
        @Override
        public Audience audience() {
            return component -> {
            };
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public Object handle() {
            return this;
        }
    }
}
