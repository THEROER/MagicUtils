package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Shared runtime container for MagicUtils-powered plugins and mods.
 *
 * <p>The runtime tracks core services, exposes a lightweight typed component
 * registry, and owns shutdown actions/resources that should be cleaned up when
 * the platform stops.</p>
 */
public final class MagicRuntime implements AutoCloseable {
    private final Object lifecycleLock = new Object();
    private final Platform platform;
    private final ConfigManager configManager;
    private final LoggerCore logger;
    private final @Nullable LanguageManager languageManager;
    private final PlatformLogger platformLogger;
    private final Map<Class<?>, Object> components = new LinkedHashMap<>();
    private final Map<String, Object> namedComponents = new LinkedHashMap<>();
    private final List<CloseStep> closeSteps = new ArrayList<>();
    private final List<Runnable> stateChangeListeners = new ArrayList<>();
    private final @Nullable ShutdownHookRegistrar shutdownRegistrar;
    private final @Nullable Runnable shutdownHook;
    private boolean closed;

    private MagicRuntime(Builder builder) {
        this.platform = builder.platform;
        this.configManager = builder.configManager;
        this.logger = builder.logger;
        this.languageManager = builder.languageManager;
        this.platformLogger = platform != null && platform.logger() != null
                ? platform.logger()
                : new NoopPlatformLogger();
        this.components.put(Platform.class, this.platform);
        this.components.put(ConfigManager.class, this.configManager);
        this.components.put(LoggerCore.class, this.logger);
        if (this.languageManager != null) {
            this.components.put(LanguageManager.class, this.languageManager);
        }
        this.components.putAll(builder.components);
        this.closeSteps.addAll(builder.closeSteps);
        if (builder.manageConfigManager) {
            this.closeSteps.add(new CloseStep("configManager", this.configManager::shutdown));
        }

        ShutdownHookRegistrar registrar = null;
        Runnable hook = null;
        if (builder.autoRegisterShutdown && this.platform instanceof ShutdownHookRegistrar resolvedRegistrar) {
            registrar = resolvedRegistrar;
            hook = this::close;
            registrar.registerShutdownHook(hook);
        }
        this.shutdownRegistrar = registrar;
        this.shutdownHook = hook;
    }

    /**
     * Creates a builder for a runtime rooted in the given core services.
     *
     * @param platform platform adapter
     * @param configManager config manager
     * @param logger logger core
     * @return builder instance
     */
    public static Builder builder(Platform platform, ConfigManager configManager, LoggerCore logger) {
        return new Builder(platform, configManager, logger);
    }

    /**
     * Returns the platform adapter.
     *
     * @return platform instance
     */
    public Platform platform() {
        return platform;
    }

    /**
     * Returns the config manager owned by the runtime.
     *
     * @return config manager
     */
    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * Returns the logger core.
     *
     * @return logger core
     */
    public LoggerCore logger() {
        return logger;
    }

    /**
     * Returns the language manager when one was configured.
     *
     * @return language manager or null
     */
    public @Nullable LanguageManager languageManager() {
        return languageManager;
    }

    /**
     * Returns a snapshot of registered typed components.
     *
     * @return immutable component map snapshot
     */
    public Map<Class<?>, Object> components() {
        synchronized (lifecycleLock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(components));
        }
    }

    /**
     * Returns a snapshot of named components registered in the runtime.
     *
     * @return immutable named component map snapshot
     */
    public Map<String, Object> namedComponents() {
        synchronized (lifecycleLock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(namedComponents));
        }
    }

    /**
     * Returns true when the runtime has already been closed.
     *
     * @return closed flag
     */
    public boolean isClosed() {
        synchronized (lifecycleLock) {
            return closed;
        }
    }

    /**
     * Registers or replaces a typed component.
     *
     * @param type component key
     * @param component component instance
     * @param <T> component type
     * @return the provided component
     */
    public <T> T putComponent(Class<T> type, T component) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(component, "component");
        synchronized (lifecycleLock) {
            components.put(type, component);
        }
        notifyStateChanged();
        return component;
    }

    /**
     * Registers or replaces a named component.
     *
     * @param name component name
     * @param component component instance
     * @param <T> component type
     * @return the provided component
     */
    public <T> T putNamedComponent(String name, T component) {
        Objects.requireNonNull(component, "component");
        synchronized (lifecycleLock) {
            namedComponents.put(normalizeName(name, "component"), component);
        }
        notifyStateChanged();
        return component;
    }

    /**
     * Removes a named component.
     *
     * @param name component name
     * @return removed component or null when absent
     */
    public @Nullable Object removeNamedComponent(String name) {
        Object removed;
        synchronized (lifecycleLock) {
            removed = namedComponents.remove(normalizeName(name, "component"));
        }
        if (removed != null) {
            notifyStateChanged();
        }
        return removed;
    }

    /**
     * Registers a listener that is invoked whenever runtime component state changes.
     *
     * @param listener listener to invoke after component registry updates
     * @return runtime for chaining
     */
    public MagicRuntime onStateChanged(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lifecycleLock) {
            if (!closed) {
                stateChangeListeners.add(listener);
            }
        }
        return this;
    }

    /**
     * Registers a shutdown action.
     *
     * @param name debug label for logging
     * @param action action to run during close
     * @return runtime for chaining
     */
    public MagicRuntime onClose(String name, Runnable action) {
        Objects.requireNonNull(action, "action");
        registerCloseStep(new CloseStep(normalizeName(name, "shutdownAction"), action::run));
        return this;
    }

    /**
     * Registers a closeable resource.
     *
     * @param name debug label for logging
     * @param closeable closeable resource
     * @param <T> closeable type
     * @return the same resource
     */
    public <T extends AutoCloseable> @Nullable T manage(String name, @Nullable T closeable) {
        if (closeable == null) {
            return null;
        }
        registerCloseStep(new CloseStep(normalizeName(name, closeable.getClass().getSimpleName()), closeable::close));
        return closeable;
    }

    /**
     * Returns a component by exact or assignable type.
     *
     * @param type component type
     * @param <T> component type
     * @return optional component
     */
    public <T> Optional<T> findComponent(Class<T> type) {
        Objects.requireNonNull(type, "type");
        synchronized (lifecycleLock) {
            Object exact = components.get(type);
            if (type.isInstance(exact)) {
                return Optional.of(type.cast(exact));
            }
            for (Object value : components.values()) {
                if (type.isInstance(value)) {
                    return Optional.of(type.cast(value));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a named component by name.
     *
     * @param name component name
     * @return optional component
     */
    public Optional<Object> findNamedComponent(String name) {
        synchronized (lifecycleLock) {
            return Optional.ofNullable(namedComponents.get(normalizeName(name, "component")));
        }
    }

    /**
     * Returns a named component when it matches the requested type.
     *
     * @param name component name
     * @param type expected component type
     * @param <T> component type
     * @return optional typed component
     */
    public <T> Optional<T> findNamedComponent(String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return findNamedComponent(name).filter(type::isInstance).map(type::cast);
    }

    /**
     * Returns a component or throws when missing.
     *
     * @param type component type
     * @param <T> component type
     * @return component instance
     */
    public <T> T requireComponent(Class<T> type) {
        return findComponent(type).orElseThrow(() ->
                new IllegalStateException("MagicRuntime component is not registered: " + type.getName()));
    }

    /**
     * Returns a named component or throws when missing.
     *
     * @param name component name
     * @param type expected component type
     * @param <T> component type
     * @return component instance
     */
    public <T> T requireNamedComponent(String name, Class<T> type) {
        return findNamedComponent(name, type).orElseThrow(() ->
                new IllegalStateException("MagicRuntime named component is not registered: "
                        + normalizeName(name, "component") + " (" + type.getName() + ")"));
    }

    /**
     * Creates a managed runtime resource handle.
     *
     * @param name resource name used for runtime registration
     * @param <T> resource type
     * @return managed resource handle
     */
    public <T extends AutoCloseable> MagicRuntimeResource<T> resource(String name) {
        return resource(name, null);
    }

    /**
     * Creates a managed runtime resource handle with an initial resource.
     *
     * @param name resource name used for runtime registration
     * @param initial initial resource, optionally null
     * @param <T> resource type
     * @return managed resource handle
     */
    public <T extends AutoCloseable> MagicRuntimeResource<T> resource(String name, @Nullable T initial) {
        return new MagicRuntimeResource<>(this, normalizeName(name, "resource"), initial);
    }

    /**
     * Creates a config-aware managed resource binding that rebuilds the resource
     * when the target config changes.
     *
     * @param name resource name used for runtime registration
     * @param configClass config class driving the resource
     * @param factory resource factory receiving the current config instance
     * @param sections optional sections that should trigger a rebuild
     * @param <C> config type
     * @param <T> resource type
     * @return config-aware managed resource binding
     */
    public <C, T extends AutoCloseable> MagicRuntimeConfigBinding<C, T> bindConfig(
            String name,
            Class<C> configClass,
            Function<C, ? extends T> factory,
            String... sections
    ) {
        return new MagicRuntimeConfigBinding<>(
                this,
                normalizeName(name, "resource"),
                configClass,
                factory,
                sections
        );
    }

    PlatformLogger platformLogger() {
        return platformLogger;
    }

    @Override
    public void close() {
        List<CloseStep> toClose;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            if (shutdownRegistrar != null && shutdownHook != null) {
                shutdownRegistrar.unregisterShutdownHook(shutdownHook);
            }
            toClose = new ArrayList<>(closeSteps);
            closeSteps.clear();
            stateChangeListeners.clear();
        }

        for (int index = toClose.size() - 1; index >= 0; index--) {
            CloseStep step = toClose.get(index);
            try {
                step.action().run();
            } catch (Exception error) {
                platformLogger.warn("Failed to close MagicRuntime resource '" + step.name() + "'", error);
            }
        }
    }

    private void registerCloseStep(CloseStep step) {
        synchronized (lifecycleLock) {
            if (!closed) {
                closeSteps.add(step);
                return;
            }
        }

        try {
            step.action().run();
        } catch (Exception error) {
            platformLogger.warn("Failed to close MagicRuntime resource '" + step.name() + "'", error);
        }
    }

    private void notifyStateChanged() {
        List<Runnable> listeners;
        synchronized (lifecycleLock) {
            if (closed || stateChangeListeners.isEmpty()) {
                return;
            }
            listeners = List.copyOf(stateChangeListeners);
        }

        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException error) {
                platformLogger.warn("Failed to handle MagicRuntime state change listener", error);
            }
        }
    }

    private static String normalizeName(@Nullable String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record CloseStep(String name, CloseAction action) {
    }

    @FunctionalInterface
    private interface CloseAction {
        void run() throws Exception;
    }

    /**
     * Builder for {@link MagicRuntime}.
     */
    public static final class Builder {
        private final Platform platform;
        private final ConfigManager configManager;
        private final LoggerCore logger;
        private final Map<Class<?>, Object> components = new LinkedHashMap<>();
        private final List<CloseStep> closeSteps = new ArrayList<>();
        private @Nullable LanguageManager languageManager;
        private boolean manageConfigManager = true;
        private boolean autoRegisterShutdown = true;

        private Builder(Platform platform, ConfigManager configManager, LoggerCore logger) {
            this.platform = Objects.requireNonNull(platform, "platform");
            this.configManager = Objects.requireNonNull(configManager, "configManager");
            this.logger = Objects.requireNonNull(logger, "logger");
        }

        /**
         * Sets an optional language manager for the runtime.
         *
         * @param languageManager language manager
         * @return builder for chaining
         */
        public Builder languageManager(@Nullable LanguageManager languageManager) {
            this.languageManager = languageManager;
            return this;
        }

        /**
         * Registers an extra typed component.
         *
         * @param type component key
         * @param component component value
         * @param <T> component type
         * @return builder for chaining
         */
        public <T> Builder component(Class<T> type, T component) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(component, "component");
            components.put(type, component);
            return this;
        }

        /**
         * Registers a closeable resource to be closed when the runtime stops.
         *
         * @param name debug label
         * @param closeable closeable resource
         * @return builder for chaining
         */
        public Builder manage(String name, AutoCloseable closeable) {
            Objects.requireNonNull(closeable, "closeable");
            closeSteps.add(new CloseStep(normalizeName(name, closeable.getClass().getSimpleName()), closeable::close));
            return this;
        }

        /**
         * Registers a shutdown action.
         *
         * @param name debug label
         * @param action action to execute
         * @return builder for chaining
         */
        public Builder onClose(String name, Runnable action) {
            Objects.requireNonNull(action, "action");
            closeSteps.add(new CloseStep(normalizeName(name, "shutdownAction"), action::run));
            return this;
        }

        /**
         * Controls whether the runtime should call {@link ConfigManager#shutdown()} on close.
         *
         * @param manageConfigManager true to shutdown the config manager
         * @return builder for chaining
         */
        public Builder manageConfigManager(boolean manageConfigManager) {
            this.manageConfigManager = manageConfigManager;
            return this;
        }

        /**
         * Controls whether the runtime should auto-register its close hook with the platform.
         *
         * @param autoRegisterShutdown true to auto-register with {@link ShutdownHookRegistrar}
         * @return builder for chaining
         */
        public Builder autoRegisterShutdown(boolean autoRegisterShutdown) {
            this.autoRegisterShutdown = autoRegisterShutdown;
            return this;
        }

        /**
         * Builds a runtime instance.
         *
         * @return runtime instance
         */
        public MagicRuntime build() {
            return new MagicRuntime(this);
        }
    }

    private static final class NoopPlatformLogger implements PlatformLogger {
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
