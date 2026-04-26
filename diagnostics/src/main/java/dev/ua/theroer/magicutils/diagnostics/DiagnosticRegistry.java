package dev.ua.theroer.magicutils.diagnostics;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Mutable registry of available runtime checks.
 */
public interface DiagnosticRegistry {
    /**
     * Returns a namespaced registry view that prefixes ids and suites with the given namespace.
     *
     * @param namespace registry namespace
     * @return namespaced registry view
     */
    default DiagnosticRegistry namespaced(String namespace) {
        return new NamespacedDiagnosticRegistry(this, namespace);
    }

    /**
     * Registers or replaces a check.
     *
     * @param check check to register
     */
    void register(DiagnosticCheck check);

    /**
     * Registers or replaces multiple checks.
     *
     * @param checks checks to register
     */
    void registerAll(Collection<? extends DiagnosticCheck> checks);

    /**
     * Removes a check by id.
     *
     * @param id check id
     * @return removed check, when present
     */
    Optional<DiagnosticCheck> unregister(String id);

    /**
     * Finds a check by id.
     *
     * @param id check id
     * @return optional check
     */
    Optional<DiagnosticCheck> find(String id);

    /**
     * Snapshot of all checks in execution order.
     *
     * @return ordered check snapshot
     */
    List<DiagnosticCheck> checks();

    /**
     * Snapshot of checks within a suite.
     *
     * @param suite suite identifier
     * @return ordered suite snapshot
     */
    List<DiagnosticCheck> checks(String suite);
}
