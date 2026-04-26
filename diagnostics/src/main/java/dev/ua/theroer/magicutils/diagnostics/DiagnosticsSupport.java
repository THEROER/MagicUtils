package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Factory and runtime wiring helpers for diagnostics.
 */
public final class DiagnosticsSupport {
    private DiagnosticsSupport() {
    }

    /**
     * Installs the default diagnostics service and built-in checks into a runtime.
     *
     * @param runtime runtime container
     * @return installed diagnostics service
     */
    public static DiagnosticsService install(MagicRuntime runtime) {
        return install(runtime, null);
    }

    /**
     * Installs the default diagnostics service and lets callers register custom checks.
     *
     * @param runtime runtime container
     * @param registryConfigurer custom registry configurer
     * @return installed diagnostics service
     */
    public static DiagnosticsService install(
            MagicRuntime runtime,
            @Nullable Consumer<DiagnosticRegistry> registryConfigurer
    ) {
        Objects.requireNonNull(runtime, "runtime");
        DefaultDiagnosticRegistry registry = new DefaultDiagnosticRegistry();
        BuiltinDiagnosticChecks.registerDefaults(registry.namespaced("magicutils"));
        if (registryConfigurer != null) {
            registryConfigurer.accept(registry);
        }
        DiagnosticsService service = new DefaultDiagnosticsService(runtime, registry);
        runtime.putComponent(DiagnosticRegistry.class, registry);
        runtime.putComponent(DiagnosticsService.class, service);
        runtime.putNamedComponent("diagnostics.registry", registry);
        runtime.putNamedComponent("diagnostics.service", service);
        return service;
    }
}
