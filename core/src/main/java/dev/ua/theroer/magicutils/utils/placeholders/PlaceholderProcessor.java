package dev.ua.theroer.magicutils.utils.placeholders;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.MsgFmt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Handles custom placeholder processing with optional audience context.
 */
public final class PlaceholderProcessor {

    private static final Pattern FORMAT_SPECIFIER_PATTERN = Pattern
            .compile("%(?!%)(?:[\\d$#+\\- 0,(<]*)(?:\\d+)?(?:\\.\\d+)?[a-zA-Z]");

    private static final Map<String, Function<Object[], String>> GLOBAL_PLACEHOLDERS =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<Object, Map<String, BiFunction<Audience, Object[], String>>> LOCAL_PLACEHOLDERS =
            Collections.synchronizedMap(new WeakHashMap<>());

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
        LOCAL_PLACEHOLDERS.computeIfAbsent(ownerKey, p -> Collections.synchronizedMap(new LinkedHashMap<>()))
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

    private static String applyDefinedPlaceholders(String input, Audience audience, Object[] args, Object ownerKey) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        result = applyResolvers(result, GLOBAL_PLACEHOLDERS, args, null);
        if (ownerKey != null) {
            Map<String, BiFunction<Audience, Object[], String>> locals = LOCAL_PLACEHOLDERS.get(ownerKey);
            if (locals != null && !locals.isEmpty()) {
                result = applyResolvers(result, locals, args, audience);
            }
        }
        return result;
    }

    private static <T> String applyResolvers(String input, Map<String, T> resolvers, Object[] args, Audience audience) {
        String result = input;
        for (Map.Entry<String, T> entry : resolvers.entrySet()) {
            String replacement = resolve(entry.getValue(), audience, args);
            if (replacement != null) {
                result = result.replace("{" + entry.getKey() + "}", replacement);
            }
        }
        return result;
    }

    private static String resolve(Object resolver, Audience audience, Object[] args) {
        try {
            if (resolver instanceof Function<?, ?> function) {
                @SuppressWarnings("unchecked")
                Function<Object[], String> fn = (Function<Object[], String>) function;
                return fn.apply(args);
            }
            if (resolver instanceof BiFunction<?, ?, ?> biFunction) {
                @SuppressWarnings("unchecked")
                BiFunction<Audience, Object[], String> fn = (BiFunction<Audience, Object[], String>) biFunction;
                return fn.apply(audience, args);
            }
        } catch (Throwable ignored) {
        }
        return null;
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
}
