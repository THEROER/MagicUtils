package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

final class DefaultDiagnosticsService implements DiagnosticsService {
    private final MagicRuntime runtime;
    private final DiagnosticRegistry registry;

    DefaultDiagnosticsService(MagicRuntime runtime, DiagnosticRegistry registry) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public MagicRuntime runtime() {
        return runtime;
    }

    @Override
    public DiagnosticRegistry registry() {
        return registry;
    }

    @Override
    public DiagnosticReport runAll(DiagnosticRunRequest request) {
        DiagnosticRunRequest resolved = request != null ? request : DiagnosticRunRequest.safe();
        return runSelection(registry.checks(), resolved);
    }

    @Override
    public DiagnosticReport runSuite(String suite, DiagnosticRunRequest request) {
        DiagnosticRunRequest resolved = request != null ? request : DiagnosticRunRequest.safe();
        if (suite == null || suite.isBlank()) {
            return runSelection(registry.checks(), resolved);
        }
        return runSelection(registry.checks(suite), resolved);
    }

    @Override
    public DiagnosticReport runChecks(Collection<String> ids, DiagnosticRunRequest request) {
        DiagnosticRunRequest resolved = request != null ? request : DiagnosticRunRequest.safe();
        Set<String> normalizedIds = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    normalizedIds.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        Predicate<DiagnosticCheck> filter = check -> normalizedIds.isEmpty()
                || normalizedIds.contains(check.id().trim().toLowerCase(Locale.ROOT));
        return runSelection(registry.checks(), resolved.withFilter(filter));
    }

    @Override
    public Path exportJson(DiagnosticReport report, Path path) {
        return DiagnosticReports.writeJson(report, path);
    }

    private DiagnosticReport runSelection(List<DiagnosticCheck> sourceChecks, DiagnosticRunRequest request) {
        Instant startedAt = Instant.now();
        DefaultDiagnosticContext context = new DefaultDiagnosticContext(runtime, request.mode(), request.verbose(), startedAt);
        List<DiagnosticCheck> selectedChecks = new ArrayList<>();
        for (DiagnosticCheck check : sourceChecks) {
            if (check != null && request.includes(check)) {
                selectedChecks.add(check);
            }
        }
        List<DiagnosticResult> results = new ArrayList<>();
        for (DiagnosticCheck check : selectedChecks) {
            results.add(runCheck(check, context, request.timeout()));
        }
        return new DiagnosticReport(
                resolveRuntimeName(runtime),
                request.mode(),
                startedAt,
                Duration.between(startedAt, Instant.now()),
                buildTechnicalReport(sourceChecks, selectedChecks, request, context),
                results
        );
    }

    private Map<String, Object> buildTechnicalReport(
            List<DiagnosticCheck> sourceChecks,
            List<DiagnosticCheck> selectedChecks,
            DiagnosticRunRequest request,
            DefaultDiagnosticContext context
    ) {
        LinkedHashMap<String, Object> technical = new LinkedHashMap<>();
        technical.put("request", requestInfo(request));
        technical.put("runtime", runtimeInfo(context));
        technical.put("environment", environmentInfo());
        technical.put("selection", selectionInfo(sourceChecks, selectedChecks, request));
        technical.put("checks", selectedCheckInfo(selectedChecks));
        return Map.copyOf(technical);
    }

    private DiagnosticResult runCheck(DiagnosticCheck check, DiagnosticContext context, Duration timeout) {
        Instant startedAt = Instant.now();
        if (!check.supportedModes().contains(context.mode())) {
            return DiagnosticResult.skipped(
                    check.id(),
                    check.suite(),
                    check.severity(),
                    "Check is not enabled for " + context.mode().name() + " mode",
                    detailMap("mode", context.mode().name())
            ).withDuration(Duration.between(startedAt, Instant.now()));
        }
        try {
            CompletionStage<DiagnosticResult> stage = check.run(context);
            if (stage == null) {
                return DiagnosticResult.fail(
                        check.id(),
                        check.suite(),
                        check.severity(),
                        "Check returned no result",
                        Map.of(),
                        null
                ).withDuration(Duration.between(startedAt, Instant.now()));
            }
            DiagnosticResult result = stage.toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (result == null) {
                return DiagnosticResult.fail(
                        check.id(),
                        check.suite(),
                        check.severity(),
                        "Check returned a null result",
                        Map.of(),
                        null
                ).withDuration(Duration.between(startedAt, Instant.now()));
            }
            return result.withDuration(Duration.between(startedAt, Instant.now()));
        } catch (TimeoutException error) {
            return DiagnosticResult.fail(
                    check.id(),
                    check.suite(),
                    check.severity(),
                    "Check timed out after " + timeout.toMillis() + " ms",
                    detailMap("timeoutMillis", timeout.toMillis()),
                    error
            ).withDuration(Duration.between(startedAt, Instant.now()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return DiagnosticResult.fail(
                    check.id(),
                    check.suite(),
                    check.severity(),
                    "Check was interrupted",
                    Map.of(),
                    error
            ).withDuration(Duration.between(startedAt, Instant.now()));
        } catch (ExecutionException | CompletionException error) {
            Throwable cause = unwrap(error);
            return DiagnosticResult.fail(
                    check.id(),
                    check.suite(),
                    check.severity(),
                    "Check failed: " + DiagnosticReports.sanitizeMessage(cause.getMessage()),
                    Map.of(),
                    cause
            ).withDuration(Duration.between(startedAt, Instant.now()));
        } catch (Throwable error) {
            return DiagnosticResult.fail(
                    check.id(),
                    check.suite(),
                    check.severity(),
                    "Check failed: " + DiagnosticReports.sanitizeMessage(error.getMessage()),
                    Map.of(),
                    error
            ).withDuration(Duration.between(startedAt, Instant.now()));
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String resolveRuntimeName(MagicRuntime runtime) {
        Path configDir = runtime.platform().configDir();
        if (configDir != null && configDir.getFileName() != null) {
            return configDir.getFileName().toString();
        }
        return runtime.platform().getClass().getSimpleName();
    }

    private Map<String, Object> runtimeInfo(DefaultDiagnosticContext context) {
        LinkedHashMap<String, Object> runtimeInfo = new LinkedHashMap<>();
        runtimeInfo.put("class", runtime.getClass().getName());
        runtimeInfo.put("closed", runtime.isClosed());
        runtimeInfo.put("thread", threadInfo(context));
        runtimeInfo.put("platform", platformInfo(context));
        runtimeInfo.put("components", componentInfo(runtime.components(), runtime.namedComponents()));
        return Map.copyOf(runtimeInfo);
    }

    private static Map<String, Object> threadInfo(DefaultDiagnosticContext context) {
        Thread current = Thread.currentThread();
        LinkedHashMap<String, Object> threadInfo = new LinkedHashMap<>();
        threadInfo.put("name", current.getName());
        threadInfo.put("state", current.getState().name());
        threadInfo.put("threadContext", context.platform().threadContext().name());
        threadInfo.put("isMainThread", context.platform().isMainThread());
        return Map.copyOf(threadInfo);
    }

    private static Map<String, Object> platformInfo(DefaultDiagnosticContext context) {
        LinkedHashMap<String, Object> platformInfo = new LinkedHashMap<>();
        platformInfo.put("providerClass", context.platform().getClass().getName());
        platformInfo.put("loggerClass", className(context.platform().logger()));
        platformInfo.put("schedulerClass", className(context.scheduler()));
        platformInfo.put("consoleClass", className(context.platform().console()));
        platformInfo.put("configDir", absolutePath(context.workingDirectory()));
        return Map.copyOf(platformInfo);
    }

    private static Map<String, Object> componentInfo(
            Map<Class<?>, Object> typedComponents,
            Map<String, Object> namedComponents
    ) {
        LinkedHashMap<String, Object> components = new LinkedHashMap<>();
        components.put("typed", typedComponentMap(typedComponents));
        components.put("named", namedComponentMap(namedComponents));
        return Map.copyOf(components);
    }

    private static Map<String, Object> typedComponentMap(Map<Class<?>, Object> typedComponents) {
        LinkedHashMap<String, Object> typed = new LinkedHashMap<>();
        if (typedComponents != null) {
            for (Map.Entry<Class<?>, Object> entry : typedComponents.entrySet()) {
                Class<?> key = entry.getKey();
                if (key != null) {
                    typed.put(key.getName(), className(entry.getValue()));
                }
            }
        }
        return Map.copyOf(typed);
    }

    private static Map<String, Object> namedComponentMap(Map<String, Object> namedComponents) {
        LinkedHashMap<String, Object> named = new LinkedHashMap<>();
        if (namedComponents != null) {
            for (Map.Entry<String, Object> entry : namedComponents.entrySet()) {
                String key = entry.getKey();
                if (key != null && !key.isBlank()) {
                    named.put(key, className(entry.getValue()));
                }
            }
        }
        return Map.copyOf(named);
    }

    private static Map<String, Object> environmentInfo() {
        LinkedHashMap<String, Object> environment = new LinkedHashMap<>();
        environment.put("javaVersion", System.getProperty("java.version"));
        environment.put("javaVendor", System.getProperty("java.vendor"));
        environment.put("javaVmName", System.getProperty("java.vm.name"));
        environment.put("osName", System.getProperty("os.name"));
        environment.put("osVersion", System.getProperty("os.version"));
        environment.put("osArch", System.getProperty("os.arch"));
        environment.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        environment.put("maxMemoryBytes", Runtime.getRuntime().maxMemory());
        environment.put("totalMemoryBytes", Runtime.getRuntime().totalMemory());
        environment.put("freeMemoryBytes", Runtime.getRuntime().freeMemory());
        return Map.copyOf(environment);
    }

    private static Map<String, Object> requestInfo(DiagnosticRunRequest request) {
        LinkedHashMap<String, Object> requestInfo = new LinkedHashMap<>();
        requestInfo.put("mode", request.mode().name());
        requestInfo.put("verbose", request.verbose());
        requestInfo.put("timeoutMillis", request.timeout().toMillis());
        requestInfo.put("filterActive", request.filter() != null);
        return Map.copyOf(requestInfo);
    }

    private static Map<String, Object> selectionInfo(
            List<DiagnosticCheck> sourceChecks,
            List<DiagnosticCheck> selectedChecks,
            DiagnosticRunRequest request
    ) {
        LinkedHashMap<String, Object> selectionInfo = new LinkedHashMap<>();
        selectionInfo.put("sourceCheckCount", sourceChecks != null ? sourceChecks.size() : 0);
        selectionInfo.put("selectedCheckCount", selectedChecks != null ? selectedChecks.size() : 0);
        selectionInfo.put("filtered", request.filter() != null);
        return Map.copyOf(selectionInfo);
    }

    private static List<Map<String, Object>> selectedCheckInfo(List<DiagnosticCheck> selectedChecks) {
        if (selectedChecks == null || selectedChecks.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> checks = new ArrayList<>(selectedChecks.size());
        for (DiagnosticCheck check : selectedChecks) {
            if (check == null) {
                continue;
            }
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();
            info.put("checkId", check.id());
            info.put("suite", check.suite());
            info.put("description", check.description());
            info.put("severity", check.severity().name());
            info.put("supportedModes", check.supportedModes().stream().map(Enum::name).toList());
            checks.add(Map.copyOf(info));
        }
        return List.copyOf(checks);
    }

    private static String absolutePath(@Nullable Path path) {
        if (path == null) {
            return "";
        }
        return path.toAbsolutePath().toString();
    }

    private static String className(@Nullable Object value) {
        return value != null ? value.getClass().getName() : "";
    }

    private static Map<String, Object> detailMap(String key, @Nullable Object value) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        if (key != null && !key.isBlank() && value != null) {
            details.put(key, value);
        }
        return Map.copyOf(details);
    }
}
