package dev.ua.theroer.magicutils.diagnostics;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class DefaultDiagnosticRegistry implements DiagnosticRegistry {
    private final Object lock = new Object();
    private final Map<String, DiagnosticCheck> checks = new LinkedHashMap<>();

    @Override
    public void register(DiagnosticCheck check) {
        Objects.requireNonNull(check, "check");
        synchronized (lock) {
            checks.put(normalize(check.id()), check);
        }
    }

    @Override
    public void registerAll(Collection<? extends DiagnosticCheck> checks) {
        if (checks == null) {
            return;
        }
        for (DiagnosticCheck check : checks) {
            if (check != null) {
                register(check);
            }
        }
    }

    @Override
    public Optional<DiagnosticCheck> unregister(String id) {
        synchronized (lock) {
            return Optional.ofNullable(checks.remove(normalize(id)));
        }
    }

    @Override
    public Optional<DiagnosticCheck> find(String id) {
        synchronized (lock) {
            return Optional.ofNullable(checks.get(normalize(id)));
        }
    }

    @Override
    public List<DiagnosticCheck> checks() {
        synchronized (lock) {
            return List.copyOf(checks.values());
        }
    }

    @Override
    public List<DiagnosticCheck> checks(String suite) {
        String normalizedSuite = normalize(suite);
        List<DiagnosticCheck> result = new ArrayList<>();
        synchronized (lock) {
            for (DiagnosticCheck check : checks.values()) {
                if (normalize(check.suite()).equals(normalizedSuite)) {
                    result.add(check);
                }
            }
        }
        return List.copyOf(result);
    }

    private static String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
