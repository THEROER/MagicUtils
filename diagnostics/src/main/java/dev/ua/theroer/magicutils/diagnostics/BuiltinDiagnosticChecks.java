package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.HelpCommandSupport;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.Tasks;
import dev.ua.theroer.magicutils.platform.ThreadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

final class BuiltinDiagnosticChecks {
    private BuiltinDiagnosticChecks() {
    }

    static void registerDefaults(DiagnosticRegistry registry) {
        registry.registerAll(java.util.List.of(
                check("runtime.platform.present", "runtime", "Platform component is available",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(runtimePlatformPresent(context))),
                check("runtime.logger.present", "runtime", "Logger core is available",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(runtimeLoggerPresent(context))),
                check("runtime.config.present", "runtime", "Config manager is available",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(runtimeConfigPresent(context))),
                check("runtime.language.present", "runtime", "Language manager is available when configured",
                        DiagnosticSeverity.INFO, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(runtimeLanguagePresent(context))),
                check("runtime.components.snapshot", "runtime", "Runtime component snapshot is accessible",
                        DiagnosticSeverity.WARNING, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(runtimeComponentsSnapshot(context))),
                check("filesystem.config_dir.exists", "filesystem", "Config directory exists",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(configDirExists(context))),
                check("filesystem.config_dir.writable", "filesystem", "Config directory is writable",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(configDirWritable(context))),
                check("config.reloadability", "config", "Config manager can execute a reload probe",
                        DiagnosticSeverity.WARNING, EnumSet.allOf(DiagnosticMode.class),
                        BuiltinDiagnosticChecks::configReloadability),
                check("scheduler.present", "scheduler", "Task scheduler is available",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(schedulerPresent(context))),
                check("scheduler.delayed_task", "scheduler", "Delayed scheduler probe completes",
                        DiagnosticSeverity.CRITICAL, EnumSet.allOf(DiagnosticMode.class),
                        BuiltinDiagnosticChecks::schedulerDelayedTask),
                check("threading.main_thread_probe", "threading", "Main-thread dispatch probe executes",
                        DiagnosticSeverity.WARNING, EnumSet.allOf(DiagnosticMode.class),
                        BuiltinDiagnosticChecks::mainThreadProbe),
                check("commands.registry.present", "commands", "Command registry is exposed in runtime components",
                        DiagnosticSeverity.INFO, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(commandRegistryPresent(context))),
                check("commands.manager.present", "commands", "Command manager is exposed in runtime components",
                        DiagnosticSeverity.WARNING, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(commandManagerPresent(context))),
                check("commands.help_render", "commands", "Help renderer can build output",
                        DiagnosticSeverity.WARNING, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(commandHelpRender(context))),
                check("placeholders.registry.access", "placeholders", "Placeholder registry snapshots are readable",
                        DiagnosticSeverity.INFO, EnumSet.allOf(DiagnosticMode.class),
                        context -> CompletableFuture.completedFuture(placeholdersRegistryAccess(context)))
        ));
    }

    private static DiagnosticResult runtimePlatformPresent(DiagnosticContext context) {
        if (context.runtime().isClosed()) {
            return DiagnosticResult.fail("runtime.platform.present", "runtime", DiagnosticSeverity.CRITICAL,
                    "MagicRuntime is already closed", Map.of(), null);
        }
        Platform platform = context.platform();
        return DiagnosticResult.ok("runtime.platform.present", "runtime", DiagnosticSeverity.CRITICAL,
                "Platform is available",
                details(
                        "type", platform.getClass().getName(),
                        "threadContext", platform.threadContext().name()
                ));
    }

    private static DiagnosticResult runtimeLoggerPresent(DiagnosticContext context) {
        LoggerCore logger = context.findComponent(LoggerCore.class).orElse(null);
        if (logger == null) {
            return DiagnosticResult.fail("runtime.logger.present", "runtime", DiagnosticSeverity.CRITICAL,
                    "LoggerCore is not registered", Map.of(), null);
        }
        return DiagnosticResult.ok("runtime.logger.present", "runtime", DiagnosticSeverity.CRITICAL,
                "LoggerCore is registered", details("type", logger.getClass().getName()));
    }

    private static DiagnosticResult runtimeConfigPresent(DiagnosticContext context) {
        ConfigManager configManager = context.findComponent(ConfigManager.class).orElse(null);
        if (configManager == null) {
            return DiagnosticResult.fail("runtime.config.present", "runtime", DiagnosticSeverity.CRITICAL,
                    "ConfigManager is not registered", Map.of(), null);
        }
        return DiagnosticResult.ok("runtime.config.present", "runtime", DiagnosticSeverity.CRITICAL,
                "ConfigManager is registered", details("configDir", context.workingDirectory().toAbsolutePath().toString()));
    }

    private static DiagnosticResult runtimeLanguagePresent(DiagnosticContext context) {
        LanguageManager languageManager = context.findComponent(LanguageManager.class).orElse(null);
        if (languageManager == null) {
            return DiagnosticResult.skipped("runtime.language.present", "runtime", DiagnosticSeverity.INFO,
                    "LanguageManager is not registered for this runtime", Map.of());
        }
        return DiagnosticResult.ok("runtime.language.present", "runtime", DiagnosticSeverity.INFO,
                "LanguageManager is registered", details("type", languageManager.getClass().getName()));
    }

    private static DiagnosticResult runtimeComponentsSnapshot(DiagnosticContext context) {
        return DiagnosticResult.ok("runtime.components.snapshot", "runtime", DiagnosticSeverity.WARNING,
                "Runtime component snapshots are readable",
                details(
                        "typedComponents", context.runtime().components().size(),
                        "namedComponents", context.runtime().namedComponents().size()
                ));
    }

    private static DiagnosticResult configDirExists(DiagnosticContext context) {
        Path configDir = context.workingDirectory();
        if (Files.exists(configDir)) {
            if (!Files.isDirectory(configDir)) {
                return DiagnosticResult.fail("filesystem.config_dir.exists", "filesystem", DiagnosticSeverity.CRITICAL,
                        "Config path exists but is not a directory",
                        details("path", configDir.toAbsolutePath().toString()), null);
            }
            return DiagnosticResult.ok("filesystem.config_dir.exists", "filesystem", DiagnosticSeverity.CRITICAL,
                    "Config directory exists", details("path", configDir.toAbsolutePath().toString()));
        }
        if (context.mode() == DiagnosticMode.STANDARD) {
            try {
                Files.createDirectories(configDir);
                return DiagnosticResult.warn("filesystem.config_dir.exists", "filesystem", DiagnosticSeverity.CRITICAL,
                        "Config directory was missing and has been created",
                        details("path", configDir.toAbsolutePath().toString()));
            } catch (IOException error) {
                return DiagnosticResult.fail("filesystem.config_dir.exists", "filesystem", DiagnosticSeverity.CRITICAL,
                        "Failed to create config directory: " + DiagnosticReports.sanitizeMessage(error.getMessage()),
                        details("path", configDir.toAbsolutePath().toString()), error);
            }
        }
        return DiagnosticResult.fail("filesystem.config_dir.exists", "filesystem", DiagnosticSeverity.CRITICAL,
                "Config directory does not exist",
                details("path", configDir.toAbsolutePath().toString()), null);
    }

    private static DiagnosticResult configDirWritable(DiagnosticContext context) {
        Path configDir = context.workingDirectory();
        if (!Files.exists(configDir) || !Files.isDirectory(configDir)) {
            return DiagnosticResult.fail("filesystem.config_dir.writable", "filesystem", DiagnosticSeverity.CRITICAL,
                    "Config directory is unavailable",
                    details("path", configDir.toAbsolutePath().toString()), null);
        }
        if (context.mode() == DiagnosticMode.SAFE) {
            return Files.isWritable(configDir)
                    ? DiagnosticResult.ok("filesystem.config_dir.writable", "filesystem", DiagnosticSeverity.CRITICAL,
                    "Config directory appears writable",
                    details("path", configDir.toAbsolutePath().toString(), "probe", "metadata"))
                    : DiagnosticResult.fail("filesystem.config_dir.writable", "filesystem", DiagnosticSeverity.CRITICAL,
                    "Config directory is not writable",
                    details("path", configDir.toAbsolutePath().toString(), "probe", "metadata"), null);
        }

        Path probe = null;
        try {
            probe = Files.createTempFile(configDir, "magicutils-diagnostics-", ".tmp");
            Files.deleteIfExists(probe);
            return DiagnosticResult.ok("filesystem.config_dir.writable", "filesystem", DiagnosticSeverity.CRITICAL,
                    "Config directory write probe succeeded",
                    details("path", configDir.toAbsolutePath().toString(), "probe", "temp-file"));
        } catch (IOException error) {
            return DiagnosticResult.fail("filesystem.config_dir.writable", "filesystem", DiagnosticSeverity.CRITICAL,
                    "Config directory write probe failed: " + DiagnosticReports.sanitizeMessage(error.getMessage()),
                    details("path", configDir.toAbsolutePath().toString(), "probe", "temp-file"), error);
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static CompletionStage<DiagnosticResult> configReloadability(DiagnosticContext context) {
        if (context.mode() == DiagnosticMode.SAFE) {
            return CompletableFuture.completedFuture(DiagnosticResult.skipped(
                    "config.reloadability",
                    "config",
                    DiagnosticSeverity.WARNING,
                    "Reload probe is disabled in SAFE mode",
                    Map.of("mode", "SAFE")
            ));
        }
        ConfigManager configManager = context.requireComponent(ConfigManager.class);
        return configManager.reloadAllAsync().thenApply(ignored ->
                DiagnosticResult.ok("config.reloadability", "config", DiagnosticSeverity.WARNING,
                        "Config reload probe completed",
                        Map.of("mode", context.mode().name())));
    }

    private static DiagnosticResult schedulerPresent(DiagnosticContext context) {
        TaskScheduler scheduler = context.scheduler();
        if (scheduler == null || scheduler.cpu() == null || scheduler.io() == null || scheduler.scheduler() == null) {
            return DiagnosticResult.fail("scheduler.present", "scheduler", DiagnosticSeverity.CRITICAL,
                    "TaskScheduler is incomplete or missing", Map.of(), null);
        }
        return DiagnosticResult.ok("scheduler.present", "scheduler", DiagnosticSeverity.CRITICAL,
                "TaskScheduler is available", details("type", scheduler.getClass().getName()));
    }

    private static CompletionStage<DiagnosticResult> schedulerDelayedTask(DiagnosticContext context) {
        TaskScheduler scheduler = context.scheduler();
        if (scheduler == null || scheduler.scheduler() == null) {
            return CompletableFuture.completedFuture(DiagnosticResult.fail(
                    "scheduler.delayed_task",
                    "scheduler",
                    DiagnosticSeverity.CRITICAL,
                    "TaskScheduler is unavailable",
                    Map.of(),
                    null
            ));
        }
        CompletableFuture<DiagnosticResult> future = new CompletableFuture<>();
        long startedAt = System.nanoTime();
        scheduler.scheduler().schedule(() -> future.complete(DiagnosticResult.ok(
                "scheduler.delayed_task",
                "scheduler",
                DiagnosticSeverity.CRITICAL,
                "Delayed task executed successfully",
                details("delayMillis", Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
        )), 25L, TimeUnit.MILLISECONDS);
        return future;
    }

    private static CompletionStage<DiagnosticResult> mainThreadProbe(DiagnosticContext context) {
        return Tasks.callOnMain(context.platform(),
                () -> new MainThreadProbe(context.platform().isMainThread(), context.platform().threadContext()))
                .thenApply(probe -> {
                    if (probe.isMainThread()) {
                        return DiagnosticResult.ok("threading.main_thread_probe", "threading",
                                DiagnosticSeverity.WARNING,
                                "Main-thread probe executed on the platform main thread",
                                details("threadContext", probe.threadContext().name()));
                    }
                    return DiagnosticResult.warn("threading.main_thread_probe", "threading",
                            DiagnosticSeverity.WARNING,
                            "runOnMain completed, but the platform did not report main-thread execution",
                            details("threadContext", probe.threadContext().name()));
                });
    }

    private static DiagnosticResult commandRegistryPresent(DiagnosticContext context) {
        Object registry = context.runtime().findNamedComponent("commandRegistry").orElse(null);
        if (registry == null) {
            return DiagnosticResult.skipped("commands.registry.present", "commands", DiagnosticSeverity.INFO,
                    "Command registry is not exposed for this runtime", Map.of());
        }
        return DiagnosticResult.ok("commands.registry.present", "commands", DiagnosticSeverity.INFO,
                "Command registry is exposed", details("type", registry.getClass().getName()));
    }

    private static DiagnosticResult commandManagerPresent(DiagnosticContext context) {
        CommandManager<?> commandManager = context.runtime()
                .findNamedComponent("commandManager", CommandManager.class)
                .orElse(null);
        if (commandManager == null) {
            Object registry = context.runtime().findNamedComponent("commandRegistry").orElse(null);
            if (registry == null) {
                return DiagnosticResult.skipped("commands.manager.present", "commands", DiagnosticSeverity.WARNING,
                        "Command manager is not exposed for this runtime", Map.of());
            }
            return DiagnosticResult.fail("commands.manager.present", "commands", DiagnosticSeverity.WARNING,
                    "Command registry is present but the command manager is missing", Map.of(), null);
        }
        Collection<?> commands = commandManager.getAll();
        return DiagnosticResult.ok("commands.manager.present", "commands", DiagnosticSeverity.WARNING,
                "Command manager is exposed",
                details("commandCount", commands != null ? commands.size() : 0));
    }

    private static DiagnosticResult commandHelpRender(DiagnosticContext context) {
        CommandManager<?> commandManager = context.runtime()
                .findNamedComponent("commandManager", CommandManager.class)
                .orElse(null);
        if (commandManager == null) {
            return DiagnosticResult.skipped("commands.help_render", "commands", DiagnosticSeverity.WARNING,
                    "Help renderer skipped because the command manager is unavailable", Map.of());
        }
        HelpCommandSupport.HelpResult result = HelpCommandSupport.build(
                commandManager,
                null,
                null,
                "diagnostics",
                context.logger(),
                null
        );
        if (!result.success()) {
            return DiagnosticResult.fail("commands.help_render", "commands", DiagnosticSeverity.WARNING,
                    "Help renderer failed: " + result.errorMessage(), Map.of(), null);
        }
        return DiagnosticResult.ok("commands.help_render", "commands", DiagnosticSeverity.WARNING,
                "Help renderer completed successfully",
                details("lineCount", result.lines().size()));
    }

    private static DiagnosticResult placeholdersRegistryAccess(DiagnosticContext context) {
        Map<?, ?> entries = MagicPlaceholders.entries();
        Collection<String> namespaces = MagicPlaceholders.namespaces();
        return DiagnosticResult.ok("placeholders.registry.access", "placeholders", DiagnosticSeverity.INFO,
                "Placeholder registry snapshots are readable",
                details(
                        "entryCount", entries.size(),
                        "namespaceCount", namespaces.size()
                ));
    }

    private static DiagnosticCheck check(
            String id,
            String suite,
            String description,
            DiagnosticSeverity severity,
            EnumSet<DiagnosticMode> supportedModes,
            CheckedRunner runner
    ) {
        return new BasicDiagnosticCheck(id, suite, description, severity, supportedModes, runner);
    }

    private static Map<String, Object> details(Object... values) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            Object key = values[index];
            Object value = values[index + 1];
            if (key instanceof String stringKey && !stringKey.isBlank() && value != null) {
                details.put(stringKey, value);
            }
        }
        return Map.copyOf(details);
    }

    private record MainThreadProbe(boolean isMainThread, ThreadContext threadContext) {
    }

    @FunctionalInterface
    private interface CheckedRunner {
        CompletionStage<DiagnosticResult> run(DiagnosticContext context);
    }

    private record BasicDiagnosticCheck(
            String id,
            String suite,
            String description,
            DiagnosticSeverity severity,
            EnumSet<DiagnosticMode> supportedModes,
            CheckedRunner runner
    ) implements DiagnosticCheck {
        private BasicDiagnosticCheck {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(suite, "suite");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(severity, "severity");
            supportedModes = supportedModes != null && !supportedModes.isEmpty()
                    ? EnumSet.copyOf(supportedModes)
                    : EnumSet.allOf(DiagnosticMode.class);
            Objects.requireNonNull(runner, "runner");
        }

        @Override
        public CompletionStage<DiagnosticResult> run(DiagnosticContext context) {
            return runner.run(context);
        }
    }
}
