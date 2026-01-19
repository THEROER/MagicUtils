package dev.ua.theroer.magicutils.platform.neoforge;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Method;

/**
 * Utility for converting Adventure components to NeoForge chat components.
 */
public final class NeoForgeComponentSerializer {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static volatile boolean serializerChecked;
    private static volatile Method fromJsonElement;
    private static volatile Method fromJsonString;

    private NeoForgeComponentSerializer() {
    }

    /**
     * Converts an Adventure component to a native Minecraft component.
     *
     * @param component adventure component (nullable)
     * @return native component
     */
    public static net.minecraft.network.chat.Component toNative(Component component) {
        if (component == null) {
            return net.minecraft.network.chat.Component.empty();
        }
        try {
            JsonElement tree = GSON.serializeToTree(component);
            net.minecraft.network.chat.Component decoded = decode(tree);
            if (decoded != null) {
                return decoded;
            }
        } catch (Exception ignored) {
        }
        return net.minecraft.network.chat.Component.literal(PLAIN.serialize(component));
    }

    private static net.minecraft.network.chat.Component decode(JsonElement tree) {
        ensureSerializer();
        if (fromJsonElement != null) {
            try {
                Object res = fromJsonElement.invoke(null, tree);
                if (res instanceof net.minecraft.network.chat.Component) {
                    return (net.minecraft.network.chat.Component) res;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (fromJsonString != null) {
            try {
                Object res = fromJsonString.invoke(null, tree.toString());
                if (res instanceof net.minecraft.network.chat.Component) {
                    return (net.minecraft.network.chat.Component) res;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static void ensureSerializer() {
        if (serializerChecked) {
            return;
        }
        serializerChecked = true;
        try {
            Class<?> serializerClass = Class.forName("net.minecraft.network.chat.Component$Serializer");
            fromJsonElement = findMethod(serializerClass, JsonElement.class);
            fromJsonString = findMethod(serializerClass, String.class);
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static Method findMethod(Class<?> serializerClass, Class<?> argType) {
        try {
            Method method = serializerClass.getMethod("fromJson", argType);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
