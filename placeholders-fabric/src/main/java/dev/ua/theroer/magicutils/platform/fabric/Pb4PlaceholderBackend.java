package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.config.logger.DefaultSettings;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * pb4 placeholder backend that adapts across pb4 API generations via
 * reflection, so the same module compiles for obfuscated (≤1.21.x) and
 * deobfuscated (26.x) targets:
 *
 * - Handler interface: {@code PlaceholderHandler} (old) or the nested
 *   {@code Placeholder.Handler} (pb4 3.x); both declare
 *   {@code onPlaceholderRequest(ctx, arg)}.
 * - Registration method: {@code Placeholders.register} (old) or
 *   {@code registerCommon} (pb4 3.x), both {@code (Identifier, handler)}.
 *
 * The handler is created as a {@link Proxy} over whichever interface is
 * present, so no pb4-version-specific type is referenced statically.
 */
final class Pb4PlaceholderBackend implements FabricPlaceholderBackend {
    private static final int MAX_LOG_VALUE = 256;

    private static final Class<?> HANDLER_TYPE = ReflectiveAccess.loadFirstAvailable(
            "eu.pb4.placeholders.api.Placeholder$Handler",
            "eu.pb4.placeholders.api.PlaceholderHandler"
    ).orElse(null);

    private static final Method REGISTER_METHOD = resolveRegisterMethod();
    private static final Method REMOVE_METHOD = resolveMethod("remove");
    private static final Method RESULT_VALUE_METHOD = resolveResultValue();
    private static final Method CONTEXT_PLAYER_METHOD = resolveContextPlayer();

    private final Set<MagicPlaceholders.PlaceholderKey> registered = ConcurrentHashMap.newKeySet();
    private final LoggerCore logger;

    Pb4PlaceholderBackend() {
        this(null);
    }

    Pb4PlaceholderBackend(LoggerCore logger) {
        this.logger = logger;
    }

    @Override
    public void registerAll() {
        for (MagicPlaceholders.PlaceholderKey key : MagicPlaceholders.entries().keySet()) {
            register(key);
        }
    }

    @Override
    public void register(MagicPlaceholders.PlaceholderKey key) {
        if (key == null || !registered.add(key)) {
            return;
        }
        Object identifier = FabricIdentifierBridge.create(key.namespace(), key.key());
        if (identifier == null || REGISTER_METHOD == null || HANDLER_TYPE == null) {
            registered.remove(key);
            return;
        }
        Object handler = Proxy.newProxyInstance(
                HANDLER_TYPE.getClassLoader(),
                new Class<?>[]{HANDLER_TYPE},
                new HandlerAdapter(key)
        );
        ReflectiveAccess.invoke(REGISTER_METHOD, null, identifier, handler);
    }

    @Override
    public void unregister(MagicPlaceholders.PlaceholderKey key) {
        if (key == null) {
            return;
        }
        registered.remove(key);
        Object identifier = FabricIdentifierBridge.create(key.namespace(), key.key());
        if (identifier == null || REMOVE_METHOD == null) {
            return;
        }
        ReflectiveAccess.invoke(REMOVE_METHOD, null, identifier);
    }

    @Override
    public void updateNamespace(String namespace) {
    }

    /**
     * Reflective bridge for the pb4 handler interface. Both API generations
     * declare a single {@code onPlaceholderRequest(ctx, arg)} method; anything
     * else (e.g. Object methods) is handled generically.
     */
    private final class HandlerAdapter implements InvocationHandler {
        private final MagicPlaceholders.PlaceholderKey key;

        private HandlerAdapter(MagicPlaceholders.PlaceholderKey key) {
            this.key = key;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("onPlaceholderRequest".equals(method.getName()) && args != null && args.length == 2) {
                String arg = args[1] instanceof String s ? s : null;
                return resolve(key, args[0], arg);
            }
            return switch (method.getName()) {
                case "toString" -> "MagicUtilsPb4Handler(" + key.namespace() + ":" + key.key() + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args != null ? args[0] : null);
                default -> null;
            };
        }
    }

    private Object resolve(MagicPlaceholders.PlaceholderKey key, Object ctx, String arg) {
        Object player = ctx != null && CONTEXT_PLAYER_METHOD != null
                ? ReflectiveAccess.invoke(CONTEXT_PLAYER_METHOD, ctx).orElse(null)
                : null;
        // FabricAudience takes a ServerPlayer; player() may be typed as Player
        // on pb4 3.x, so guard by runtime type.
        Audience audience = player instanceof net.minecraft.server.level.ServerPlayer sp
                ? new FabricAudience(sp)
                : null;
        String value = MagicPlaceholders.resolve(key.namespace(), key.key(), audience, arg);
        if (value == null || value.isEmpty()) {
            logDebug(key, arg, audience, value, Component.empty(), DefaultSettings.Pb4Mode.COMPONENT);
            return placeholderValue(net.minecraft.network.chat.Component.empty());
        }
        DefaultSettings.Pb4Mode mode = DefaultSettings.Pb4Mode.COMPONENT;
        if (logger != null && logger.getConfig() != null) {
            DefaultSettings defaults = logger.getConfig().getDefaults();
            if (defaults != null && defaults.getPb4Mode() != null) {
                mode = defaults.getPb4Mode();
            }
        }
        if (mode == DefaultSettings.Pb4Mode.RAW) {
            logDebug(key, arg, audience, value, null, mode);
            return placeholderValue(net.minecraft.network.chat.Component.literal(value));
        }
        Component parsed = MessageParser.parseSmart(value);
        logDebug(key, arg, audience, value, parsed, mode);
        return placeholderValue(FabricComponentSerializer.toNative(parsed));
    }

    private static Object placeholderValue(net.minecraft.network.chat.Component component) {
        return RESULT_VALUE_METHOD != null
                ? ReflectiveAccess.invoke(RESULT_VALUE_METHOD, null, component).orElse(null)
                : null;
    }

    private void logDebug(MagicPlaceholders.PlaceholderKey key,
                          String argument,
                          Audience audience,
                          String raw,
                          Component parsed,
                          DefaultSettings.Pb4Mode mode) {
        if (!isDebugEnabled()) {
            return;
        }
        String keyName = key != null ? key.namespace() + ":" + key.key() : "unknown";
        String uuid = audience != null && audience.id() != null ? audience.id().toString() : "null";
        String mini = parsed != null ? logger.getMiniMessage().serialize(parsed) : "";
        String plain = parsed != null ? FabricComponentSerializer.toPlain(parsed) : "";
        logger.getPlatform().logger().info(
                "[MagicUtils][Placeholders][PB4] key=" + keyName
                        + " arg=" + sanitize(argument)
                        + " uuid=" + uuid
                        + " raw=" + sanitize(raw)
                        + " mode=" + mode
                        + " mini=" + sanitize(mini)
                        + " plain=" + sanitize(plain)
        );
    }

    private boolean isDebugEnabled() {
        if (logger == null || logger.getConfig() == null) {
            return false;
        }
        return logger.getConfig().isDebugPlaceholders();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() > MAX_LOG_VALUE) {
            return normalized.substring(0, MAX_LOG_VALUE) + "...(" + normalized.length() + ")";
        }
        return normalized;
    }

    /** register (old pb4) or registerCommon (pb4 3.x): {@code (Identifier, handler)}. */
    private static Method resolveRegisterMethod() {
        Class<?> identifierType = FabricIdentifierBridge.type();
        if (identifierType == null || HANDLER_TYPE == null) {
            return null;
        }
        Class<?> placeholders = ReflectiveAccess
                .loadClass("eu.pb4.placeholders.api.Placeholders")
                .orElse(null);
        if (placeholders == null) {
            return null;
        }
        return ReflectiveAccess.firstMethod(placeholders, method -> {
            String name = method.getName();
            if (!name.equals("registerCommon") && !name.equals("register")) {
                return false;
            }
            Class<?>[] params = method.getParameterTypes();
            return params.length == 2
                    && params[0].isAssignableFrom(identifierType)
                    && params[1].isAssignableFrom(HANDLER_TYPE);
        }).orElse(null);
    }

    private static Method resolveResultValue() {
        return ReflectiveAccess.loadClass("eu.pb4.placeholders.api.PlaceholderResult")
                .flatMap(type -> ReflectiveAccess.publicMethod(
                        type, "value", net.minecraft.network.chat.Component.class))
                .orElse(null);
    }

    private static Method resolveContextPlayer() {
        return ReflectiveAccess.loadClass("eu.pb4.placeholders.api.PlaceholderContext")
                .flatMap(type -> ReflectiveAccess.publicMethod(type, "player"))
                .orElse(null);
    }

    private static Method resolveMethod(String name, Class<?>... trailingTypes) {
        Class<?> identifierType = FabricIdentifierBridge.type();
        if (identifierType == null) {
            return null;
        }
        Class<?> placeholders = ReflectiveAccess
                .loadClass("eu.pb4.placeholders.api.Placeholders")
                .orElse(null);
        if (placeholders == null) {
            return null;
        }
        Class<?>[] parameterTypes = new Class<?>[trailingTypes.length + 1];
        parameterTypes[0] = identifierType;
        System.arraycopy(trailingTypes, 0, parameterTypes, 1, trailingTypes.length);
        return ReflectiveAccess.publicMethod(placeholders, name, parameterTypes).orElse(null);
    }

}
