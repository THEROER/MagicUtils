package dev.ua.theroer.magicutils.bootstrap;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Managed resource slot owned by a {@link MagicRuntime}.
 *
 * <p>The slot keeps the current resource under a stable name, exposes it
 * through the runtime's named component registry, and closes replaced/current
 * resources automatically.</p>
 *
 * @param <T> resource type
 */
public final class MagicRuntimeResource<T extends AutoCloseable> implements AutoCloseable {
    private final Object stateLock = new Object();
    private final MagicRuntime runtime;
    private final String name;
    private @Nullable T current;
    private boolean closed;

    MagicRuntimeResource(MagicRuntime runtime, String name, @Nullable T initial) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.name = Objects.requireNonNull(name, "name");
        if (initial != null) {
            current = initial;
            runtime.putNamedComponent(name, initial);
        }
        runtime.onClose("runtimeResource:" + name, this::close);
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
     * Returns true when the slot has been closed.
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
        synchronized (stateLock) {
            return Optional.ofNullable(current);
        }
    }

    /**
     * Returns the current resource or throws when absent.
     *
     * @return current resource
     */
    public T require() {
        return current().orElseThrow(() ->
                new IllegalStateException("MagicRuntime resource is not available: " + name));
    }

    /**
     * Installs a new resource, closing the previous one.
     *
     * @param next next resource, optionally null
     * @return the installed resource, or null when cleared
     */
    public @Nullable T set(@Nullable T next) {
        T previous;
        synchronized (stateLock) {
            if (closed) {
                closeQuietly(next);
                return null;
            }
            previous = current;
            current = next;
            if (next != null) {
                runtime.putNamedComponent(name, next);
            } else {
                runtime.removeNamedComponent(name);
            }
        }

        if (previous != next) {
            closeQuietly(previous);
        }
        return next;
    }

    @Override
    public void close() {
        T previous;
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            closed = true;
            previous = current;
            current = null;
        }

        runtime.removeNamedComponent(name);
        closeQuietly(previous);
    }

    private void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception error) {
            runtime.platformLogger().warn("Failed to close MagicRuntime resource '" + name + "'", error);
        }
    }
}
