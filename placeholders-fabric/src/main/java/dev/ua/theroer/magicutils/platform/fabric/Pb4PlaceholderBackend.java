package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.config.logger.DefaultSettings;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Audience;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderHandler;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.kyori.adventure.text.Component;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class Pb4PlaceholderBackend implements FabricPlaceholderBackend {
    private static final int MAX_LOG_VALUE = 256;
    private static final Method REGISTER_METHOD = resolveMethod("register", PlaceholderHandler.class);
    private static final Method REMOVE_METHOD = resolveMethod("remove");
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
        if (identifier == null || REGISTER_METHOD == null) {
            registered.remove(key);
            return;
        }
        ReflectiveAccess.invoke(
                REGISTER_METHOD,
                null,
                identifier,
                (PlaceholderHandler) (ctx, arg) -> resolve(key, ctx, arg)
        );
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

    private PlaceholderResult resolve(MagicPlaceholders.PlaceholderKey key, PlaceholderContext ctx, String arg) {
        Audience audience = ctx != null && ctx.player() != null ? new FabricAudience(ctx.player()) : null;
        String value = MagicPlaceholders.resolve(key.namespace(), key.key(), audience, arg);
        if (value == null || value.isEmpty()) {
            logDebug(key, arg, audience, value, Component.empty(), DefaultSettings.Pb4Mode.COMPONENT);
            return PlaceholderResult.value(net.minecraft.network.chat.Component.empty());
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
            return PlaceholderResult.value(net.minecraft.network.chat.Component.literal(value));
        }
        Component parsed = MessageParser.parseSmart(value);
        logDebug(key, arg, audience, value, parsed, mode);
        return PlaceholderResult.value(FabricComponentSerializer.toNative(parsed));
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

    private static Method resolveMethod(String name, Class<?>... trailingTypes) {
        Class<?> identifierType = FabricIdentifierBridge.type();
        if (identifierType == null) {
            return null;
        }
        Class<?>[] parameterTypes = new Class<?>[trailingTypes.length + 1];
        parameterTypes[0] = identifierType;
        System.arraycopy(trailingTypes, 0, parameterTypes, 1, trailingTypes.length);
        return ReflectiveAccess.publicMethod(Placeholders.class, name, parameterTypes).orElse(null);
    }

}
