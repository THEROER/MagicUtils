package dev.ua.theroer.magicutils.config.serialization;

import dev.ua.theroer.magicutils.config.adapters.EnumAdapter;

import java.util.Collections;
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

    /**
     * Lookup adapter for a type.
     *
     * @param <T>  target type
     * @param type target class
     * @return adapter or null if not registered
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigValueAdapter<T> get(Class<T> type) {
        if (type == null) {
            return null;
        }
        ConfigValueAdapter<T> adapter = (ConfigValueAdapter<T>) ADAPTERS.get(type);
        if (adapter == null && type.isEnum()) {
            @SuppressWarnings({"rawtypes"})
            EnumAdapter<?> enumAdapter = new EnumAdapter(type.asSubclass(Enum.class));
            ADAPTERS.put(type, enumAdapter);
            adapter = (ConfigValueAdapter<T>) enumAdapter;
        }
        return adapter;
    }

    /**
     * Returns true if an adapter is registered for the given type.
     *
     * @param type class to check
     * @return true when present
     */
    public static boolean has(Class<?> type) {
        return ADAPTERS.containsKey(type);
    }

    /**
     * Provides read-only access to registered adapters.
     *
     * @return unmodifiable view of adapters map
     */
    public static Map<Class<?>, ConfigValueAdapter<?>> getAdaptersView() {
        return Collections.unmodifiableMap(ADAPTERS);
    }
}
