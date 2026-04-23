package dev.ua.theroer.magicutils.diagnostics;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

final class NamespacedDiagnosticRegistry implements DiagnosticRegistry {
    private final DiagnosticRegistry delegate;
    private final String namespace;
    private final String namespacePrefix;

    NamespacedDiagnosticRegistry(DiagnosticRegistry delegate, String namespace) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.namespace = normalizeNamespace(namespace);
        this.namespacePrefix = this.namespace + ".";
    }

    @Override
    public void register(DiagnosticCheck check) {
        if (check == null) {
            return;
        }
        delegate.register(new NamespacedDiagnosticCheck(check, namespacePrefix));
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
        return delegate.unregister(prefix(id));
    }

    @Override
    public Optional<DiagnosticCheck> find(String id) {
        return delegate.find(prefix(id));
    }

    @Override
    public List<DiagnosticCheck> checks() {
        return delegate.checks().stream()
                .filter(check -> startsWithNamespace(check.id()))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<DiagnosticCheck> checks(String suite) {
        return delegate.checks(prefix(suite));
    }

    private boolean startsWithNamespace(@Nullable String value) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(namespacePrefix);
    }

    private String prefix(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return namespace;
        }
        String normalized = value.trim();
        return normalized.toLowerCase(Locale.ROOT).startsWith(namespacePrefix) ? normalized : namespacePrefix + normalized;
    }

    private static String normalizeNamespace(@Nullable String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace is required");
        }
        return namespace.trim().toLowerCase(Locale.ROOT);
    }

    private static final class NamespacedDiagnosticCheck implements DiagnosticCheck {
        private final DiagnosticCheck delegate;
        private final String namespacePrefix;
        private final String id;
        private final String suite;

        private NamespacedDiagnosticCheck(DiagnosticCheck delegate, String namespacePrefix) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.namespacePrefix = Objects.requireNonNull(namespacePrefix, "namespacePrefix");
            this.id = prefix(delegate.id(), namespacePrefix);
            this.suite = prefix(delegate.suite(), namespacePrefix);
        }

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
            return delegate.description();
        }

        @Override
        public DiagnosticSeverity severity() {
            return delegate.severity();
        }

        @Override
        public EnumSet<DiagnosticMode> supportedModes() {
            return delegate.supportedModes();
        }

        @Override
        public CompletionStage<DiagnosticResult> run(DiagnosticContext context) {
            return delegate.run(context).thenApply(this::namespaceResult);
        }

        private DiagnosticResult namespaceResult(DiagnosticResult result) {
            if (result == null) {
                return null;
            }
            return new DiagnosticResult(
                    prefix(result.checkId(), namespacePrefix),
                    prefix(result.suite(), namespacePrefix),
                    result.status(),
                    result.severity(),
                    result.message(),
                    result.duration(),
                    result.details(),
                    result.error()
            );
        }

        private static String prefix(@Nullable String value, String namespacePrefix) {
            if (value == null || value.isBlank()) {
                return namespacePrefix.substring(0, namespacePrefix.length() - 1);
            }
            String normalized = value.trim();
            return normalized.toLowerCase(Locale.ROOT).startsWith(namespacePrefix) ? normalized : namespacePrefix + normalized;
        }
    }
}
