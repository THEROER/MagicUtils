package dev.ua.theroer.magicutils.config.serialization;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple registry for config value adapters.
 */
public final class ConfigAdapters {
    private static final Map<Class<?>, ConfigValueAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();

    private ConfigAdapters() {
    }

    /**
     * Register an adapter for the given type.
     *
     * @param <T>  target type
     * @param type target class
     * @param adapter adapter implementation
     */
    public static <T> void register(Class<T> type, ConfigValueAdapter<T> adapter) {
        if (type == null || adapter == null) return;
        ADAPTERS.put(type, adapter);
    }

    @SuppressWarnings("unchecked")
    /**
     * Lookup adapter for a type.
     *
     * @param <T>  target type
     * @param type target class
     * @return adapter or null if not registered
     */
    public static <T> ConfigValueAdapter<T> get(Class<T> type) {
        return (ConfigValueAdapter<T>) ADAPTERS.get(type);
    }
}
