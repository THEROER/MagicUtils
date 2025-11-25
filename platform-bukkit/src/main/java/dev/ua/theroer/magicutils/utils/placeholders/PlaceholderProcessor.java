package dev.ua.theroer.magicutils.utils.placeholders;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.ua.theroer.magicutils.integrations.PlaceholderApiIntegration;
import dev.ua.theroer.magicutils.integrations.PlaceholderEngine;
import dev.ua.theroer.magicutils.utils.MsgFmt;

/**
 * Handles placeholder processing pipeline (custom placeholders + PlaceholderAPI).
 */
public final class PlaceholderProcessor {

    private static final Pattern FORMAT_SPECIFIER_PATTERN = Pattern
            .compile("%(?!%)(?:[\\d$#+\\- 0,(<]*)(?:\\d+)?(?:\\.\\d+)?[a-zA-Z]");

    private static final Map<JavaPlugin, PlaceholderApiIntegration> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Function<Object[], String>> GLOBAL_PLACEHOLDERS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<JavaPlugin, Map<String, BiFunction<Player, Object[], String>>> LOCAL_PLACEHOLDERS = Collections
            .synchronizedMap(new WeakHashMap<>());

    private PlaceholderProcessor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Applies custom placeholders without plugin context.
     * 
     * @param messageStr the message string
     * @param placeholders formatting arguments
     * @return processed message string
     */
    public static String applyPlaceholders(String messageStr, Object[] placeholders) {
        return applyPlaceholders(null, null, messageStr, placeholders);
    }

    /**
     * Applies custom placeholders with plugin and player context.
     * 
     * @param plugin the plugin context for local placeholders
     * @param player the player context for placeholders
     * @param messageStr the message string
     * @param placeholders formatting arguments
     * @return processed message string
     */
    public static String applyPlaceholders(JavaPlugin plugin, Player player, String messageStr, Object[] placeholders) {
        String resolved = messageStr;
        if (shouldProcessPlaceholders(messageStr, placeholders)) {
            resolved = MsgFmt.apply(messageStr, placeholders);
        }
        if (resolved == null || resolved.isEmpty()) {
            return resolved;
        }
        return applyDefinedPlaceholders(resolved, player, placeholders, plugin);
    }

    /**
     * Applies PlaceholderAPI placeholders to the message.
     * 
     * @param plugin the plugin context
     * @param messageStr the message string
     * @param target the target player
     * @return processed message string
     */
    public static String applyPapi(JavaPlugin plugin, String messageStr, Player target) {
        if (plugin == null || target == null) {
            return messageStr;
        }
        PlaceholderApiIntegration integration = CACHE.computeIfAbsent(plugin, PlaceholderApiIntegration::new);
        PlaceholderEngine engine = integration.getImplementationLazy();
        return engine.apply(target, messageStr);
    }

    /**
     * Picks the primary player from available options.
     * 
     * @param directPlayer direct player reference
     * @param collection collection of players
     * @return the primary player or null
     */
    public static Player pickPrimaryPlayer(Player directPlayer, Collection<? extends Player> collection) {
        if (directPlayer != null) {
            return directPlayer;
        }
        if (collection != null) {
            for (Player candidate : collection) {
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
     * @param key the placeholder key (without braces)
     * @param resolver function that takes formatting args and returns string
     */
    public static void registerGlobalPlaceholder(String key, Function<Object[], String> resolver) {
        if (key == null || resolver == null) {
            throw new IllegalArgumentException("key and resolver cannot be null");
        }
        GLOBAL_PLACEHOLDERS.put(normalizeKey(key), resolver);
    }

    /**
     * Registers a plugin-specific placeholder resolver.
     * 
     * @param plugin the plugin context
     * @param key the placeholder key (without braces)
     * @param resolver function that takes player and formatting args and returns string
     */
    public static void registerLocalPlaceholder(JavaPlugin plugin, String key, BiFunction<Player, Object[], String> resolver) {
        if (plugin == null || key == null || resolver == null) {
            throw new IllegalArgumentException("plugin, key and resolver cannot be null");
        }
        LOCAL_PLACEHOLDERS.computeIfAbsent(plugin, p -> Collections.synchronizedMap(new LinkedHashMap<>()))
                .put(normalizeKey(key), resolver);
    }

    /**
     * Registers a global placeholder with a simple supplier.
     * 
     * @param key the placeholder key (without braces)
     * @param supplier function that returns string value
     */
    public static void registerGlobalPlaceholder(String key, Supplier<String> supplier) {
        registerGlobalPlaceholder(key, ignore -> supplier.get());
    }

    /**
     * Registers a plugin-specific placeholder with player context only.
     * 
     * @param plugin the plugin context
     * @param key the placeholder key (without braces)
     * @param resolver function that takes player and returns string
     */
    public static void registerLocalPlaceholder(JavaPlugin plugin, String key, Function<Player, String> resolver) {
        registerLocalPlaceholder(plugin, key, (player, ignored) -> resolver.apply(player));
    }

    /**
     * Clears all local placeholders for a plugin.
     * 
     * @param plugin the plugin to clear placeholders for
     */
    public static void clearLocalPlaceholders(JavaPlugin plugin) {
        if (plugin != null) {
            LOCAL_PLACEHOLDERS.remove(plugin);
        }
    }

    private static String applyDefinedPlaceholders(String input, Player player, Object[] args, JavaPlugin plugin) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        result = applyResolvers(result, GLOBAL_PLACEHOLDERS, args, null);
        if (plugin != null) {
            Map<String, BiFunction<Player, Object[], String>> locals = LOCAL_PLACEHOLDERS.get(plugin);
            if (locals != null && !locals.isEmpty()) {
                result = applyResolvers(result, locals, args, player);
            }
        }
        return result;
    }

    private static <T> String applyResolvers(String input, Map<String, T> resolvers, Object[] args, Player player) {
        String result = input;
        for (Map.Entry<String, T> entry : resolvers.entrySet()) {
            String replacement = resolve(entry.getValue(), player, args);
            if (replacement != null) {
                result = result.replace("{" + entry.getKey() + "}", replacement);
            }
        }
        return result;
    }

    private static String resolve(Object resolver, Player player, Object[] args) {
        try {
            if (resolver instanceof Function<?, ?> function) {
                @SuppressWarnings("unchecked")
                Function<Object[], String> fn = (Function<Object[], String>) function;
                return fn.apply(args);
            }
            if (resolver instanceof BiFunction<?, ?, ?> biFunction) {
                @SuppressWarnings("unchecked")
                BiFunction<Player, Object[], String> fn = (BiFunction<Player, Object[], String>) biFunction;
                return fn.apply(player, args);
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