package dev.ua.theroer.magicutils.placeholders;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and helpers for MagicUtils placeholder resolution.
 */
public final class MagicPlaceholders {
    /**
     * Resolves a placeholder value.
     */
    @FunctionalInterface
    public interface PlaceholderResolver {
        /**
         * Resolves the placeholder value for an audience and argument.
         *
         * @param audience optional audience context
         * @param argument optional placeholder argument
         * @return resolved placeholder value or null
         */
        @Nullable String resolve(@Nullable Audience audience, @Nullable String argument);
    }

    /**
     * Listener for placeholder registry events.
     */
    public interface PlaceholderListener {
        /**
         * Called when a placeholder is registered.
         *
         * @param key placeholder key
         */
        void onPlaceholderRegistered(PlaceholderKey key);

        /**
         * Called when a placeholder is unregistered.
         *
         * @param key placeholder key
         */
        void onPlaceholderUnregistered(PlaceholderKey key);

        /**
         * Called when namespace metadata changes.
         *
         * @param namespace namespace identifier
         */
        void onNamespaceUpdated(String namespace);
    }

    /**
     * Metadata describing a placeholder namespace.
     */
    public static final class NamespaceMeta {
        private final String namespace;
        private String author;
        private String version;

        private NamespaceMeta(String namespace, @Nullable String author, @Nullable String version) {
            this.namespace = namespace;
            this.author = author != null ? author : namespace;
            this.version = version != null ? version : "dev";
        }

        /**
         * Returns the namespace identifier.
         *
         * @return namespace id
         */
        public String namespace() {
            return namespace;
        }

        /**
         * Returns the namespace author.
         *
         * @return author name
         */
        public String author() {
            return author;
        }

        /**
         * Updates the namespace author.
         *
         * @param author new author name
         */
        public void setAuthor(@Nullable String author) {
            this.author = author != null ? author : namespace;
        }

        /**
         * Returns the namespace version.
         *
         * @return version label
         */
        public String version() {
            return version;
        }

        /**
         * Updates the namespace version.
         *
         * @param version new version label
         */
        public void setVersion(@Nullable String version) {
            this.version = version != null ? version : "dev";
        }
    }

    /**
     * Identifier for a placeholder entry.
     *
     * @param namespace namespace id
     * @param key placeholder key
     */
    public record PlaceholderKey(String namespace, String key) {
    }

    /**
     * Result of placeholder resolution.
     *
     * @param value resolved value (may be null)
     * @param error resolution error (null on success)
     */
    public record PlaceholderResult(@Nullable String value, @Nullable Throwable error) {
        /**
         * Returns true when resolution succeeded.
         *
         * @return true when no error was recorded
         */
        public boolean success() {
            return error == null;
        }

        /**
         * Returns a non-null value or an empty string.
         *
         * @return resolved value or empty string
         */
        public String valueOrEmpty() {
            return value != null ? value : "";
        }
    }

    private static final Map<PlaceholderKey, PlaceholderResolver> PLACEHOLDERS = new ConcurrentHashMap<>();
    private static final Map<String, NamespaceMeta> NAMESPACES = new ConcurrentHashMap<>();
    private static final List<PlaceholderListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile PlaceholderDebugListener DEBUG_LISTENER;

    private MagicPlaceholders() {
    }

    /**
     * Registers or updates metadata for a namespace.
     *
     * @param namespace namespace id
     * @param author namespace author
     * @param version namespace version
     * @return metadata instance for the namespace
     */
    public static NamespaceMeta registerNamespace(String namespace, @Nullable String author, @Nullable String version) {
        String normalized = normalizeNamespace(namespace);
        NamespaceMeta meta = NAMESPACES.computeIfAbsent(normalized, key -> new NamespaceMeta(key, author, version));
        meta.setAuthor(author);
        meta.setVersion(version);
        notifyNamespaceUpdated(normalized);
        return meta;
    }

    /**
     * Returns metadata for the namespace, creating it if missing.
     *
     * @param namespace namespace id
     * @return metadata instance for the namespace
     */
    public static NamespaceMeta getNamespaceMeta(String namespace) {
        String normalized = normalizeNamespace(namespace);
        return NAMESPACES.computeIfAbsent(normalized, key -> new NamespaceMeta(key, null, null));
    }

    /**
     * Registers a placeholder resolver.
     *
     * @param namespace namespace id
     * @param key placeholder key
     * @param resolver resolver implementation
     */
    public static void register(String namespace, String key, PlaceholderResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        String normalizedNamespace = normalizeNamespace(namespace);
        getNamespaceMeta(normalizedNamespace);
        PlaceholderKey placeholderKey = new PlaceholderKey(normalizedNamespace, normalizeKey(key));
        PLACEHOLDERS.put(placeholderKey, resolver);
        notifyRegistered(placeholderKey);
    }

    /**
     * Unregisters a placeholder resolver.
     *
     * @param namespace namespace id
     * @param key placeholder key
     */
    public static void unregister(String namespace, String key) {
        PlaceholderKey placeholderKey = new PlaceholderKey(normalizeNamespace(namespace), normalizeKey(key));
        PLACEHOLDERS.remove(placeholderKey);
        notifyUnregistered(placeholderKey);
    }

    /**
     * Returns the resolver for a placeholder key.
     *
     * @param namespace namespace id
     * @param key placeholder key
     * @return resolver or null if missing
     */
    public static @Nullable PlaceholderResolver get(String namespace, String key) {
        return PLACEHOLDERS.get(new PlaceholderKey(normalizeNamespace(namespace), normalizeKey(key)));
    }

    /**
     * Resolves a placeholder value with error details.
     *
     * @param namespace namespace id
     * @param key placeholder key
     * @param audience optional audience
     * @param argument optional argument
     * @return result containing value and error details
     */
    public static PlaceholderResult resolveResult(String namespace,
                                                  String key,
                                                  @Nullable Audience audience,
                                                  @Nullable String argument) {
        PlaceholderKey placeholderKey = new PlaceholderKey(normalizeNamespace(namespace), normalizeKey(key));
        PlaceholderResolver resolver = PLACEHOLDERS.get(placeholderKey);
        if (resolver == null) {
            PlaceholderResult result = new PlaceholderResult("", null);
            notifyResolved(placeholderKey, audience, argument, result);
            return result;
        }
        try {
            String value = resolver.resolve(audience, argument);
            PlaceholderResult result = new PlaceholderResult(value, null);
            notifyResolved(placeholderKey, audience, argument, result);
            return result;
        } catch (Throwable error) {
            PlaceholderResult result = new PlaceholderResult("", error);
            notifyResolved(placeholderKey, audience, argument, result);
            return result;
        }
    }

    /**
     * Resolves a placeholder value.
     *
     * @param namespace namespace id
     * @param key placeholder key
     * @param audience optional audience
     * @param argument optional argument
     * @return resolved value or empty string on failure
     */
    public static String resolve(String namespace, String key, @Nullable Audience audience, @Nullable String argument) {
        return resolveResult(namespace, key, audience, argument).valueOrEmpty();
    }

    /**
     * Returns a snapshot of registered placeholders.
     *
     * @return copy of the placeholder map
     */
    public static Map<PlaceholderKey, PlaceholderResolver> entries() {
        return new LinkedHashMap<>(PLACEHOLDERS);
    }

    /**
     * Returns placeholder keys within a namespace.
     *
     * @param namespace namespace id
     * @return list of placeholder keys
     */
    public static List<PlaceholderKey> keysForNamespace(String namespace) {
        String normalized = normalizeNamespace(namespace);
        List<PlaceholderKey> keys = new ArrayList<>();
        for (PlaceholderKey key : PLACEHOLDERS.keySet()) {
            if (normalized.equals(key.namespace())) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Returns all registered namespaces.
     *
     * @return set of namespace ids
     */
    public static Set<String> namespaces() {
        return Collections.unmodifiableSet(NAMESPACES.keySet());
    }

    /**
     * Adds a placeholder listener.
     *
     * @param listener listener to add
     */
    public static void addListener(PlaceholderListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Removes a placeholder listener.
     *
     * @param listener listener to remove
     */
    public static void removeListener(PlaceholderListener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Sets a debug listener for placeholder resolution events.
     *
     * @param listener debug listener (null disables)
     */
    public static void setDebugListener(@Nullable PlaceholderDebugListener listener) {
        DEBUG_LISTENER = listener;
    }

    /**
     * Creates a lightweight audience wrapper for a UUID.
     *
     * @param uuid audience UUID
     * @return audience wrapper or null
     */
    public static @Nullable Audience audienceFromUuid(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return new SimpleAudience(uuid);
    }

    private static void notifyRegistered(PlaceholderKey key) {
        for (PlaceholderListener listener : LISTENERS) {
            try {
                listener.onPlaceholderRegistered(key);
            } catch (Throwable error) {
                notifyListenerError("register", key, error);
            }
        }
    }

    private static void notifyUnregistered(PlaceholderKey key) {
        for (PlaceholderListener listener : LISTENERS) {
            try {
                listener.onPlaceholderUnregistered(key);
            } catch (Throwable error) {
                notifyListenerError("unregister", key, error);
            }
        }
    }

    private static void notifyNamespaceUpdated(String namespace) {
        for (PlaceholderListener listener : LISTENERS) {
            try {
                listener.onNamespaceUpdated(namespace);
            } catch (Throwable error) {
                notifyListenerError("namespace", new PlaceholderKey(namespace, ""), error);
            }
        }
    }

    private static void notifyResolved(PlaceholderKey key,
                                       @Nullable Audience audience,
                                       @Nullable String argument,
                                       PlaceholderResult result) {
        PlaceholderDebugListener listener = DEBUG_LISTENER;
        if (listener == null) {
            return;
        }
        listener.onResolve(key, audience, argument, result.value(), result.error());
    }

    private static void notifyListenerError(String action, PlaceholderKey key, Throwable error) {
        PlaceholderDebugListener listener = DEBUG_LISTENER;
        if (listener != null) {
            listener.onListenerError(action, key, error);
        }
    }

    private static String normalizeNamespace(String namespace) {
        if (namespace == null) {
            throw new IllegalArgumentException("namespace is null");
        }
        String trimmed = namespace.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("namespace is empty");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("key is empty");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Debug listener for placeholder resolution events.
     */
    public interface PlaceholderDebugListener {
        /**
         * Called when a placeholder resolves or fails.
         *
         * @param key placeholder key
         * @param audience audience context
         * @param argument placeholder argument
         * @param value resolved value (null on error)
         * @param error error during resolution (null on success)
         */
        void onResolve(PlaceholderKey key,
                       @Nullable Audience audience,
                       @Nullable String argument,
                       @Nullable String value,
                       @Nullable Throwable error);

        /**
         * Called when a listener throws during registry notifications.
         *
         * @param action lifecycle action name
         * @param key placeholder key
         * @param error thrown error
         */
        default void onListenerError(String action, PlaceholderKey key, Throwable error) {
        }
    }

    private static final class SimpleAudience implements Audience {
        private final UUID uuid;

        private SimpleAudience(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public void send(Component component) {
        }

        @Override
        public UUID id() {
            return uuid;
        }
    }
}
