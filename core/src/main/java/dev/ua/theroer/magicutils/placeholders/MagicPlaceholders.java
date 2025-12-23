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

public final class MagicPlaceholders {
    @FunctionalInterface
    public interface PlaceholderResolver {
        @Nullable String resolve(@Nullable Audience audience, @Nullable String argument);
    }

    public interface PlaceholderListener {
        void onPlaceholderRegistered(PlaceholderKey key);

        void onPlaceholderUnregistered(PlaceholderKey key);

        void onNamespaceUpdated(String namespace);
    }

    public static final class NamespaceMeta {
        private final String namespace;
        private String author;
        private String version;

        private NamespaceMeta(String namespace, @Nullable String author, @Nullable String version) {
            this.namespace = namespace;
            this.author = author != null ? author : namespace;
            this.version = version != null ? version : "dev";
        }

        public String namespace() {
            return namespace;
        }

        public String author() {
            return author;
        }

        public void setAuthor(@Nullable String author) {
            this.author = author != null ? author : namespace;
        }

        public String version() {
            return version;
        }

        public void setVersion(@Nullable String version) {
            this.version = version != null ? version : "dev";
        }
    }

    public record PlaceholderKey(String namespace, String key) {
    }

    private static final Map<PlaceholderKey, PlaceholderResolver> PLACEHOLDERS = new ConcurrentHashMap<>();
    private static final Map<String, NamespaceMeta> NAMESPACES = new ConcurrentHashMap<>();
    private static final List<PlaceholderListener> LISTENERS = new CopyOnWriteArrayList<>();

    private MagicPlaceholders() {
    }

    public static NamespaceMeta registerNamespace(String namespace, @Nullable String author, @Nullable String version) {
        String normalized = normalizeNamespace(namespace);
        NamespaceMeta meta = NAMESPACES.computeIfAbsent(normalized, key -> new NamespaceMeta(key, author, version));
        meta.setAuthor(author);
        meta.setVersion(version);
        notifyNamespaceUpdated(normalized);
        return meta;
    }

    public static NamespaceMeta getNamespaceMeta(String namespace) {
        String normalized = normalizeNamespace(namespace);
        return NAMESPACES.computeIfAbsent(normalized, key -> new NamespaceMeta(key, null, null));
    }

    public static void register(String namespace, String key, PlaceholderResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        String normalizedNamespace = normalizeNamespace(namespace);
        getNamespaceMeta(normalizedNamespace);
        PlaceholderKey placeholderKey = new PlaceholderKey(normalizedNamespace, normalizeKey(key));
        PLACEHOLDERS.put(placeholderKey, resolver);
        notifyRegistered(placeholderKey);
    }

    public static void unregister(String namespace, String key) {
        PlaceholderKey placeholderKey = new PlaceholderKey(normalizeNamespace(namespace), normalizeKey(key));
        PLACEHOLDERS.remove(placeholderKey);
        notifyUnregistered(placeholderKey);
    }

    public static @Nullable PlaceholderResolver get(String namespace, String key) {
        return PLACEHOLDERS.get(new PlaceholderKey(normalizeNamespace(namespace), normalizeKey(key)));
    }

    public static String resolve(String namespace, String key, @Nullable Audience audience, @Nullable String argument) {
        PlaceholderResolver resolver = get(namespace, key);
        if (resolver == null) {
            return "";
        }
        try {
            String value = resolver.resolve(audience, argument);
            return value != null ? value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static Map<PlaceholderKey, PlaceholderResolver> entries() {
        return new LinkedHashMap<>(PLACEHOLDERS);
    }

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

    public static Set<String> namespaces() {
        return Collections.unmodifiableSet(NAMESPACES.keySet());
    }

    public static void addListener(PlaceholderListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(PlaceholderListener listener) {
        LISTENERS.remove(listener);
    }

    public static @Nullable Audience audienceFromUuid(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return new SimpleAudience(uuid);
    }

    private static void notifyRegistered(PlaceholderKey key) {
        for (PlaceholderListener listener : LISTENERS) {
            listener.onPlaceholderRegistered(key);
        }
    }

    private static void notifyUnregistered(PlaceholderKey key) {
        for (PlaceholderListener listener : LISTENERS) {
            listener.onPlaceholderUnregistered(key);
        }
    }

    private static void notifyNamespaceUpdated(String namespace) {
        for (PlaceholderListener listener : LISTENERS) {
            listener.onNamespaceUpdated(namespace);
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
