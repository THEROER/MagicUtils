package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Config-aware runtime resource binding.
 *
 * <p>The binding keeps a managed resource in sync with a config instance and
 * rebuilds it whenever matching config sections change.</p>
 *
 * @param <C> config type
 * @param <T> resource type
 */
public final class MagicRuntimeConfigBinding<C, T extends AutoCloseable> implements AutoCloseable {
    private final Object stateLock = new Object();
    private final MagicRuntime runtime;
    private final String name;
    private final Class<C> configClass;
    private final Function<C, ? extends T> factory;
    private final Set<String> sections;
    private final MagicRuntimeResource<T> resource;
    private final ListenerSubscription subscription;
    private boolean closed;

    MagicRuntimeConfigBinding(
            MagicRuntime runtime,
            String name,
            Class<C> configClass,
            Function<C, ? extends T> factory,
            String... sections
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.name = Objects.requireNonNull(name, "name");
        this.configClass = Objects.requireNonNull(configClass, "configClass");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.sections = normalizeSections(sections);
        this.resource = runtime.resource(name);
        this.subscription = runtime.configManager().subscribeChanges(configClass, this::handleConfigChange);
        runtime.onClose("configBinding:" + name, this::close);

        try {
            refresh();
        } catch (RuntimeException | Error error) {
            close();
            throw error;
        }
    }

    /**
     * Returns the resource name used in the runtime registry.
     *
     * @return resource name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the bound config class.
     *
     * @return config class
     */
    public Class<C> configClass() {
        return configClass;
    }

    /**
     * Returns the managed resource handle.
     *
     * @return managed resource handle
     */
    public MagicRuntimeResource<T> resource() {
        return resource;
    }

    /**
     * Returns true when the binding has been closed.
     *
     * @return closed flag
     */
    public boolean isClosed() {
        synchronized (stateLock) {
            return closed;
        }
    }

    /**
     * Returns the current resource when present.
     *
     * @return optional current resource
     */
    public Optional<T> current() {
        return resource.current();
    }

    /**
     * Returns the current resource or throws when absent.
     *
     * @return current resource
     */
    public T require() {
        return resource.require();
    }

    /**
     * Rebuilds the resource from the current config instance.
     *
     * @return rebuilt resource, or null when the factory returned null
     */
    public @Nullable T refresh() {
        ensureOpen();
        return replace(resolveConfig());
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        subscription.close();
        resource.close();
    }

    private void handleConfigChange(C config, Set<String> changedSections) {
        if (isClosed() || !matchesSections(changedSections)) {
            return;
        }

        try {
            replace(config);
        } catch (RuntimeException | Error error) {
            runtime.platformLogger().error("Failed to rebuild MagicRuntime resource '" + name
                    + "' from config " + configClass.getName(), error);
        }
    }

    private @Nullable T replace(C config) {
        T next = factory.apply(config);
        return resource.set(next);
    }

    private C resolveConfig() {
        ConfigManager configManager = runtime.configManager();
        C currentConfig = configManager.getConfig(configClass);
        return currentConfig != null ? currentConfig : configManager.register(configClass);
    }

    private boolean matchesSections(Set<String> changedSections) {
        if (sections.isEmpty() || changedSections == null || changedSections.isEmpty()) {
            return true;
        }
        for (String section : changedSections) {
            if (section == null) {
                continue;
            }
            String normalized = section.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            for (String configured : sections) {
                if (configured.equals(normalized)
                        || configured.startsWith(normalized + ".")
                        || normalized.startsWith(configured + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureOpen() {
        if (isClosed()) {
            throw new IllegalStateException("MagicRuntime config binding is closed: " + name);
        }
    }

    private static Set<String> normalizeSections(@Nullable String... sections) {
        if (sections == null || sections.length == 0) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String section : sections) {
            if (section == null) {
                continue;
            }
            String trimmed = section.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }
}
