package dev.ua.theroer.magicutils.placeholders;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
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
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Registry and helpers for MagicUtils placeholder resolution.
 */
public final class MagicPlaceholders {
    private static final String DEFAULT_ARGUMENT_SEPARATOR = "|";
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
    private static final Map<String, PlaceholderResolver> GLOBAL_PLACEHOLDERS = new ConcurrentHashMap<>();
    private static final Map<Object, Map<String, PlaceholderResolver>> LOCAL_PLACEHOLDERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, NamespaceMeta> NAMESPACES = new ConcurrentHashMap<>();
    private static final List<PlaceholderListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<WeakReference<PlaceholderListener>> WEAK_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<PlaceholderDebugListener> DEBUG_LISTENERS = new CopyOnWriteArrayList<>();

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
     * Registers a global placeholder resolver (no namespace).
     *
     * @param key placeholder key
     * @param resolver resolver implementation
     */
    public static void registerGlobal(String key, PlaceholderResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        GLOBAL_PLACEHOLDERS.put(normalizeKey(key), resolver);
    }

    /**
     * Registers a local placeholder resolver scoped to an owner.
     *
     * @param ownerKey owner scope key
     * @param key placeholder key
     * @param resolver resolver implementation
     */
    public static void registerLocal(Object ownerKey, String key, PlaceholderResolver resolver) {
        Objects.requireNonNull(ownerKey, "ownerKey");
        Objects.requireNonNull(resolver, "resolver");
        Map<String, PlaceholderResolver> locals =
                LOCAL_PLACEHOLDERS.computeIfAbsent(ownerKey, ignored -> new ConcurrentHashMap<>());
        locals.put(normalizeKey(key), resolver);
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
     * Unregisters a global placeholder resolver.
     *
     * @param key placeholder key
     */
    public static void unregisterGlobal(String key) {
        GLOBAL_PLACEHOLDERS.remove(normalizeKey(key));
    }

    /**
     * Removes all local placeholders for an owner.
     *
     * @param ownerKey owner scope key
     */
    public static void clearLocal(Object ownerKey) {
        if (ownerKey != null) {
            LOCAL_PLACEHOLDERS.remove(ownerKey);
        }
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
        PlaceholderResult result = resolveNamespaced(placeholderKey, audience, argument, null);
        if (result == null) {
            return new PlaceholderResult("", null);
        }
        return result;
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
     * Resolves placeholders inside a string using the provided context.
     *
     * @param context placeholder context
     * @param text input text
     * @return resolved text
     */
    public static String render(PlaceholderContext context, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) {
            return text;
        }
        StringBuilder buffer = new StringBuilder(text.length());
        int index = 0;
        int open = firstBrace;
        while (open >= 0) {
            buffer.append(text, index, open);
            int close = text.indexOf('}', open + 1);
            if (close < 0) {
                buffer.append(text, open, text.length());
                return buffer.toString();
            }
            int nested = text.indexOf('{', open + 1);
            if (close == open + 1 || (nested >= 0 && nested < close)) {
                buffer.append('{');
                index = open + 1;
            } else {
                String token = text.substring(open + 1, close);
                String replacement = resolveToken(token, context);
                if (replacement == null) {
                    buffer.append(text, open, close + 1);
                } else {
                    buffer.append(replacement);
                }
                index = close + 1;
            }
            open = text.indexOf('{', index);
        }
        if (index < text.length()) {
            buffer.append(text, index, text.length());
        }
        return buffer.toString();
    }

    /**
     * Resolves placeholders inside a string for an audience.
     *
     * @param audience audience context
     * @param text input text
     * @return resolved text
     */
    public static String render(@Nullable Audience audience, String text) {
        return render(PlaceholderContext.builder().audience(audience).build(), text);
    }

    /**
     * Resolves placeholders inside a string with inline replacements.
     *
     * @param audience audience context
     * @param text input text
     * @param inline inline placeholder values
     * @return resolved text
     */
    public static String render(@Nullable Audience audience, String text, Map<String, String> inline) {
        return render(PlaceholderContext.builder().audience(audience).inline(inline).build(), text);
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
     * Adds a placeholder listener as a weak reference.
     *
     * @param listener listener to add
     */
    public static void addWeakListener(PlaceholderListener listener) {
        if (listener != null) {
            WEAK_LISTENERS.add(new WeakReference<>(listener));
        }
    }

    /**
     * Removes a placeholder listener.
     *
     * @param listener listener to remove
     */
    public static void removeListener(PlaceholderListener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.remove(listener);
        WEAK_LISTENERS.removeIf(ref -> {
            PlaceholderListener target = ref.get();
            return target == null || target == listener;
        });
    }

    /**
     * Clears all registered listeners.
     */
    public static void clearListeners() {
        LISTENERS.clear();
        WEAK_LISTENERS.clear();
    }

    /**
     * Sets a debug listener for placeholder resolution events.
     *
     * @param listener debug listener (null disables)
     */
    public static void setDebugListener(@Nullable PlaceholderDebugListener listener) {
        DEBUG_LISTENERS.clear();
        if (listener != null) {
            DEBUG_LISTENERS.add(listener);
        }
    }

    /**
     * Adds a debug listener for placeholder resolution events.
     *
     * @param listener debug listener to add
     */
    public static void addDebugListener(@Nullable PlaceholderDebugListener listener) {
        if (listener != null) {
            DEBUG_LISTENERS.add(listener);
        }
    }

    /**
     * Removes a debug listener.
     *
     * @param listener listener to remove
     */
    public static void removeDebugListener(@Nullable PlaceholderDebugListener listener) {
        if (listener != null) {
            DEBUG_LISTENERS.remove(listener);
        }
    }

    /**
     * Clears all registered placeholders, namespaces, and listeners.
     *
     * <p>Intended for test teardown or hot-reload scenarios.</p>
     */
    public static void clearAll() {
        PLACEHOLDERS.clear();
        GLOBAL_PLACEHOLDERS.clear();
        LOCAL_PLACEHOLDERS.clear();
        NAMESPACES.clear();
        clearListeners();
        DEBUG_LISTENERS.clear();
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
        notifyListeners("register", key, listener -> listener.onPlaceholderRegistered(key));
    }

    private static void notifyUnregistered(PlaceholderKey key) {
        notifyListeners("unregister", key, listener -> listener.onPlaceholderUnregistered(key));
    }

    private static void notifyNamespaceUpdated(String namespace) {
        PlaceholderKey key = new PlaceholderKey(namespace, "");
        notifyListeners("namespace", key, listener -> listener.onNamespaceUpdated(namespace));
    }

    private static void notifyListeners(String action,
                                        PlaceholderKey key,
                                        Consumer<PlaceholderListener> invoker) {
        for (PlaceholderListener listener : LISTENERS) {
            try {
                invoker.accept(listener);
            } catch (Throwable error) {
                notifyListenerError(action, key, error);
            }
        }
        boolean cleanup = false;
        for (WeakReference<PlaceholderListener> ref : WEAK_LISTENERS) {
            PlaceholderListener listener = ref.get();
            if (listener == null) {
                cleanup = true;
                continue;
            }
            try {
                invoker.accept(listener);
            } catch (Throwable error) {
                notifyListenerError(action, key, error);
            }
        }
        if (cleanup) {
            WEAK_LISTENERS.removeIf(ref -> ref.get() == null);
        }
    }

    private static void notifyResolved(PlaceholderKey key,
                                       @Nullable Object ownerKey,
                                       @Nullable Audience audience,
                                       @Nullable String argument,
                                       PlaceholderResult result) {
        if (DEBUG_LISTENERS.isEmpty()) {
            return;
        }
        for (PlaceholderDebugListener listener : DEBUG_LISTENERS) {
            try {
                listener.onResolve(key, ownerKey, audience, argument, result.value(), result.error());
            } catch (Throwable ignored) {
            }
        }
    }

    private static void notifyListenerError(String action, PlaceholderKey key, Throwable error) {
        if (DEBUG_LISTENERS.isEmpty()) {
            return;
        }
        for (PlaceholderDebugListener listener : DEBUG_LISTENERS) {
            try {
                listener.onListenerError(action, key, error);
            } catch (Throwable ignored) {
            }
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

    private static String resolveToken(String token, PlaceholderContext context) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Map<String, String> inline = context != null ? context.inline() : Collections.emptyMap();
        String inlineValue = resolveInline(trimmed, inline);
        if (inlineValue != null) {
            return inlineValue;
        }

        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            return resolveNamespacedToken(trimmed, context);
        }

        Object ownerKey = context != null ? context.ownerKey() : null;
        String normalized = normalizeKeySafe(trimmed);
        String argument = null;

        String separator = context != null ? context.argumentSeparator() : null;
        if (separator == null) {
            separator = DEFAULT_ARGUMENT_SEPARATOR;
        }
        if (!separator.isEmpty()) {
            int separatorIndex = trimmed.indexOf(separator);
            if (separatorIndex > 0 && separatorIndex < trimmed.length() - separator.length()) {
                String keyPart = trimmed.substring(0, separatorIndex);
                String argumentPart = trimmed.substring(separatorIndex + separator.length());
                String normalizedKey = normalizeKeySafe(keyPart);
                if (normalizedKey != null) {
                    normalized = normalizedKey;
                    argument = argumentPart;
                }
            }
        }

        if (ownerKey != null) {
            PlaceholderResult localResult = resolveLocal(ownerKey, normalized,
                    context != null ? context.audience() : null, argument, ownerKey);
            if (localResult != null) {
                return localResult.valueOrEmpty();
            }
        }

        String defaultNamespace = context != null ? context.defaultNamespace() : null;
        if (defaultNamespace != null && !defaultNamespace.isBlank()) {
            PlaceholderKey placeholderKey = new PlaceholderKey(normalizeNamespace(defaultNamespace), normalized);
            PlaceholderResult namespaced = resolveNamespaced(placeholderKey,
                    context != null ? context.audience() : null, argument, ownerKey);
            if (namespaced != null) {
                return namespaced.valueOrEmpty();
            }
        }

        PlaceholderResult global = resolveGlobal(normalized, context != null ? context.audience() : null,
                argument, ownerKey);
        if (global != null) {
            return global.valueOrEmpty();
        }

        return null;
    }

    private static String resolveNamespacedToken(String token, PlaceholderContext context) {
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) {
            return null;
        }
        String namespace = token.substring(0, colon);
        String rest = token.substring(colon + 1);
        if (namespace.isBlank() || rest.isBlank()) {
            return null;
        }
        String key = rest;
        String argument = null;
        int second = rest.indexOf(':');
        if (second >= 0) {
            key = rest.substring(0, second);
            argument = rest.substring(second + 1);
        }
        if (key.isBlank()) {
            return null;
        }
        PlaceholderKey placeholderKey = new PlaceholderKey(normalizeNamespace(namespace), normalizeKey(key));
        PlaceholderResult result = resolveNamespaced(placeholderKey,
                context != null ? context.audience() : null,
                argument,
                context != null ? context.ownerKey() : null);
        return result != null ? result.valueOrEmpty() : null;
    }

    private static PlaceholderResult resolveLocal(Object ownerKey,
                                                  String key,
                                                  @Nullable Audience audience,
                                                  @Nullable String argument,
                                                  @Nullable Object ownerForDebug) {
        Map<String, PlaceholderResolver> locals = LOCAL_PLACEHOLDERS.get(ownerKey);
        if (locals == null) {
            return null;
        }
        PlaceholderResolver resolver = locals.get(key);
        if (resolver == null) {
            return null;
        }
        return resolveWith(resolver, new PlaceholderKey("local", key), ownerForDebug, audience, argument);
    }

    private static PlaceholderResult resolveGlobal(String key,
                                                   @Nullable Audience audience,
                                                   @Nullable String argument,
                                                   @Nullable Object ownerForDebug) {
        PlaceholderResolver resolver = GLOBAL_PLACEHOLDERS.get(key);
        if (resolver == null) {
            return null;
        }
        return resolveWith(resolver, new PlaceholderKey("global", key), ownerForDebug, audience, argument);
    }

    private static PlaceholderResult resolveNamespaced(PlaceholderKey key,
                                                       @Nullable Audience audience,
                                                       @Nullable String argument,
                                                       @Nullable Object ownerForDebug) {
        PlaceholderResolver resolver = PLACEHOLDERS.get(key);
        if (resolver == null) {
            return null;
        }
        return resolveWith(resolver, key, ownerForDebug, audience, argument);
    }

    private static PlaceholderResult resolveWith(PlaceholderResolver resolver,
                                                 PlaceholderKey key,
                                                 @Nullable Object ownerKey,
                                                 @Nullable Audience audience,
                                                 @Nullable String argument) {
        try {
            String value = resolver.resolve(audience, argument);
            PlaceholderResult result = new PlaceholderResult(value, null);
            notifyResolved(key, ownerKey, audience, argument, result);
            return result;
        } catch (Throwable error) {
            PlaceholderResult result = new PlaceholderResult("", error);
            notifyResolved(key, ownerKey, audience, argument, result);
            return result;
        }
    }

    private static String resolveInline(String key, Map<String, String> inline) {
        if (inline == null || inline.isEmpty()) {
            return null;
        }
        String value = inline.get(key);
        if (value != null) {
            return value;
        }
        String normalized = normalizeKeySafe(key);
        return normalized != null ? inline.get(normalized) : null;
    }

    private static String normalizeKeySafe(String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            return null;
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
         * @param ownerKey owner context (if any)
         * @param audience audience context
         * @param argument placeholder argument
         * @param value resolved value (null on error)
         * @param error error during resolution (null on success)
         */
        void onResolve(PlaceholderKey key,
                       @Nullable Object ownerKey,
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
