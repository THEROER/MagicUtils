package dev.ua.theroer.magicutils.utils.placeholders;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.MsgFmt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

/**
 * Handles custom placeholder processing with optional audience context.
 */
public final class PlaceholderProcessor {

    private static final Pattern FORMAT_SPECIFIER_PATTERN = Pattern
            .compile("%(?!%)(?:[\\d$#+\\- 0,(<]*)(?:\\d+)?(?:\\.\\d+)?[a-zA-Z]");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");

    private static final Map<String, Function<Object[], String>> GLOBAL_PLACEHOLDERS =
            new ConcurrentHashMap<>();
    private static final Map<Object, Map<String, BiFunction<Audience, Object[], String>>> LOCAL_PLACEHOLDERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<PlaceholderDebugListener> DEBUG_LISTENERS = new CopyOnWriteArrayList<>();

    private PlaceholderProcessor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Applies custom placeholders without owner or audience context.
     *
     * @param messageStr message string
     * @param placeholders formatting arguments
     * @return processed message string
     */
    public static String applyPlaceholders(String messageStr, Object[] placeholders) {
        return applyPlaceholders(null, null, messageStr, placeholders);
    }

    /**
     * Applies custom placeholders with owner and audience context.
     *
     * @param ownerKey owner context for local placeholders
     * @param audience audience context for placeholders
     * @param messageStr message string
     * @param placeholders formatting arguments
     * @return processed message string
     */
    public static String applyPlaceholders(Object ownerKey, Audience audience, String messageStr, Object[] placeholders) {
        String resolved = messageStr;
        if (shouldProcessPlaceholders(messageStr, placeholders)) {
            resolved = MsgFmt.apply(messageStr, placeholders);
        }
        if (resolved == null || resolved.isEmpty()) {
            return resolved;
        }
        return applyDefinedPlaceholders(resolved, audience, placeholders, ownerKey);
    }

    /**
     * Picks the primary audience from available options.
     *
     * @param direct direct audience reference
     * @param collection collection of audiences
     * @return the primary audience or null
     */
    public static Audience pickPrimaryAudience(Audience direct, Collection<? extends Audience> collection) {
        if (direct != null) {
            return direct;
        }
        if (collection != null) {
            for (Audience candidate : collection) {
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Registers a global placeholder resolver.
     *
     * @param key placeholder key (without braces)
     * @param resolver function that takes formatting args and returns string
     */
    public static void registerGlobalPlaceholder(String key, Function<Object[], String> resolver) {
        if (key == null || resolver == null) {
            throw new IllegalArgumentException("key and resolver cannot be null");
        }
        GLOBAL_PLACEHOLDERS.put(normalizeKey(key), resolver);
    }

    /**
     * Registers an owner-specific placeholder resolver.
     *
     * @param ownerKey owner context
     * @param key placeholder key (without braces)
     * @param resolver function that takes audience and formatting args
     */
    public static void registerLocalPlaceholder(Object ownerKey, String key, BiFunction<Audience, Object[], String> resolver) {
        if (ownerKey == null || key == null || resolver == null) {
            throw new IllegalArgumentException("ownerKey, key and resolver cannot be null");
        }
        LOCAL_PLACEHOLDERS.computeIfAbsent(ownerKey, p -> new ConcurrentHashMap<>())
                .put(normalizeKey(key), resolver);
    }

    /**
     * Registers a global placeholder with a simple supplier.
     *
     * @param key placeholder key (without braces)
     * @param supplier function that returns string value
     */
    public static void registerGlobalPlaceholder(String key, Supplier<String> supplier) {
        registerGlobalPlaceholder(key, ignore -> supplier.get());
    }

    /**
     * Registers an owner-specific placeholder with audience context only.
     *
     * @param ownerKey owner context
     * @param key placeholder key (without braces)
     * @param resolver function that takes audience and returns string
     */
    public static void registerLocalPlaceholder(Object ownerKey, String key, Function<Audience, String> resolver) {
        registerLocalPlaceholder(ownerKey, key, (audience, ignored) -> resolver.apply(audience));
    }

    /**
     * Clears all local placeholders for an owner.
     *
     * @param ownerKey owner context to clear placeholders for
     */
    public static void clearLocalPlaceholders(Object ownerKey) {
        if (ownerKey != null) {
            LOCAL_PLACEHOLDERS.remove(ownerKey);
        }
    }

    /**
     * Sets a debug listener invoked for placeholder resolution events.
     *
     * @param listener debug listener (null disables)
     */
    public static void setDebugListener(PlaceholderDebugListener listener) {
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
    public static void addDebugListener(PlaceholderDebugListener listener) {
        if (listener != null) {
            DEBUG_LISTENERS.add(listener);
        }
    }

    /**
     * Removes a debug listener.
     *
     * @param listener listener to remove
     */
    public static void removeDebugListener(PlaceholderDebugListener listener) {
        if (listener != null) {
            DEBUG_LISTENERS.remove(listener);
        }
    }

    private static String applyDefinedPlaceholders(String input, Audience audience, Object[] args, Object ownerKey) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        result = applyResolvers(result, GLOBAL_PLACEHOLDERS, args, null, ownerKey);
        if (ownerKey != null) {
            Map<String, BiFunction<Audience, Object[], String>> locals = LOCAL_PLACEHOLDERS.get(ownerKey);
            if (locals != null && !locals.isEmpty()) {
                result = applyResolvers(result, locals, args, audience, ownerKey);
            }
        }
        return result;
    }

    private static <T> String applyResolvers(String input,
                                             Map<String, T> resolvers,
                                             Object[] args,
                                             Audience audience,
                                             Object ownerKey) {
        if (resolvers == null || resolvers.isEmpty()) {
            return input;
        }
        Map<String, T> snapshot = snapshotResolvers(resolvers);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            T resolver = snapshot.get(key);
            if (resolver == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replacement = resolve(key, resolver, audience, args, ownerKey);
            if (replacement == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolve(String key, Object resolver, Audience audience, Object[] args, Object ownerKey) {
        try {
            if (resolver instanceof Function<?, ?> function) {
                @SuppressWarnings("unchecked")
                Function<Object[], String> fn = (Function<Object[], String>) function;
                String value = fn.apply(args);
                notifyResolved(key, ownerKey, audience, value, null);
                return value;
            }
            if (resolver instanceof BiFunction<?, ?, ?> biFunction) {
                @SuppressWarnings("unchecked")
                BiFunction<Audience, Object[], String> fn = (BiFunction<Audience, Object[], String>) biFunction;
                String value = fn.apply(audience, args);
                notifyResolved(key, ownerKey, audience, value, null);
                return value;
            }
        } catch (Throwable error) {
            notifyResolved(key, ownerKey, audience, null, error);
        }
        return null;
    }

    private static <T> Map<String, T> snapshotResolvers(Map<String, T> resolvers) {
        if (resolvers instanceof ConcurrentHashMap) {
            return new LinkedHashMap<>(resolvers);
        }
        synchronized (resolvers) {
            return new LinkedHashMap<>(resolvers);
        }
    }

    private static void notifyResolved(String key, Object ownerKey, Audience audience, String value, Throwable error) {
        if (DEBUG_LISTENERS.isEmpty()) {
            return;
        }
        for (PlaceholderDebugListener listener : DEBUG_LISTENERS) {
            try {
                listener.onResolve(key, ownerKey, audience, value, error);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean shouldProcessPlaceholders(String messageStr, Object[] placeholders) {
        if (messageStr == null || messageStr.isEmpty() || placeholders == null || placeholders.length == 0) {
            return false;
        }
        return containsCurlyPlaceholder(messageStr) || containsFormatSpecifier(messageStr);
    }

    private static boolean containsCurlyPlaceholder(String messageStr) {
        int open = messageStr.indexOf('{');
        if (open == -1) {
            return false;
        }
        int close = messageStr.indexOf('}', open);
        return close > open;
    }

    private static boolean containsFormatSpecifier(String messageStr) {
        return FORMAT_SPECIFIER_PATTERN.matcher(messageStr).find();
    }

    private static String normalizeKey(String key) {
        return key.trim();
    }

    /**
     * Debug hook for placeholder resolution.
     */
    @FunctionalInterface
    public interface PlaceholderDebugListener {
        /**
         * Called after a placeholder resolves or fails.
         *
         * @param key placeholder key
         * @param ownerKey owner context (if any)
         * @param audience audience context (if any)
         * @param value resolved value (null when unresolved or errored)
         * @param error error during resolution (null on success)
         */
        void onResolve(String key, Object ownerKey, Audience audience, String value, Throwable error);
    }
}
