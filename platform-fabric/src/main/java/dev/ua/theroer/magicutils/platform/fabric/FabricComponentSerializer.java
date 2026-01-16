package dev.ua.theroer.magicutils.platform.fabric;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility for converting between Adventure components and Fabric Text.
 */
@SuppressWarnings("doclint:missing")
public final class FabricComponentSerializer {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int DEFAULT_MAX_JSON_LENGTH = 262_144;

    private static volatile int maxJsonLength = DEFAULT_MAX_JSON_LENGTH;

    // Reflection caches (loaded lazily)
    private static volatile boolean textCodecsChecked = false;
    private static volatile Class<?> textCodecsClass;
    private static volatile Method textCodecsCodecMethod; 
    private static volatile Method textSerializerFromJsonMethod;
    private static volatile Method textSerializerToJsonTreeMethod;

    private FabricComponentSerializer() {
    }

    public static void setMaxJsonLength(int maxJsonLength) {
        FabricComponentSerializer.maxJsonLength = maxJsonLength > 0 ? maxJsonLength : DEFAULT_MAX_JSON_LENGTH;
    }

    public static int getMaxJsonLength() {
        return maxJsonLength;
    }

    public static Text toNative(Component component) {
        return toNative(component, null);
    }

    public static Text nativeParseSmart(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }
        return toNative(MessageParser.parseSmart(input));
    }

    public static Text nativeParseSmart(String template, Map<String, ?> values) {
        String formatted = values != null ? MsgFmt.apply(template, values) : template;
        return nativeParseSmart(formatted);
    }

    public static List<Text> nativeParseSmartList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .map(FabricComponentSerializer::nativeParseSmart)
                .toList();
    }

    public static Text toNative(Component component, Consumer<String> onError) {
        if (component == null) {
            return Text.empty();
        }

        String plain = PLAIN.serialize(component);

        try {
            JsonElement tree = GSON.serializeToTree(component);
            Text decoded = decodeText(tree, onError);
            return decoded != null ? decoded : Text.literal(plain);
        } catch (Exception e) {
            if (onError != null) onError.accept(e.toString());
            return Text.literal(plain);
        }
    }

    public static Component toAdventure(Text text) {
        return toAdventure(text, null);
    }

    public static Component toAdventure(Text text, RegistryWrapper.WrapperLookup registries) {
        if (text == null) {
            return Component.empty();
        }

        try {
            JsonElement tree = encodeText(text, registries);
            if (tree == null) {
                return Component.text(Objects.toString(text.getString(), ""));
            }
            return GSON.deserializeFromTree(tree);
        } catch (Exception e) {
            return Component.text(Objects.toString(text.getString(), ""));
        }
    }

    public static String toPlain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    public static Supplier<Text> fbLazyMemoized(Component component) {
        AtomicReference<Text> ref = new AtomicReference<>();

        return () -> {
            Text cached = ref.get();
            if (cached != null) return cached;

            Text created = toNative(component);
            ref.compareAndSet(null, created);
            return ref.get();
        };
    }

    /**
     * Decode JSON -> Text.
     * On 1.20.6+ / 1.21.x uses TextCodecs codec with length limit.
     * On 1.20.1 falls back to Text.Serializer.fromJson(JsonElement).
     */
    private static Text decodeText(JsonElement tree, Consumer<String> onError) {
        ensureReflectionReady();

        if (textCodecsClass != null && textCodecsCodecMethod != null) {
            try {
                Object codec = textCodecsCodecMethod.invoke(null, maxJsonLength);

                @SuppressWarnings("unchecked")
                DataResult<Text> result = (DataResult<Text>) codec.getClass()
                        .getMethod("parse", com.mojang.serialization.DynamicOps.class, Object.class)
                        .invoke(codec, JsonOps.INSTANCE, tree);

                if (onError != null) result.error().ifPresent(err -> onError.accept(err.message()));
                return result.result().orElse(null);
            } catch (Throwable t) {
                if (onError != null) onError.accept(t.toString());
                // fallback below
            }
        }

        try {
            if (textSerializerFromJsonMethod != null) {
                Object r = textSerializerFromJsonMethod.invoke(null, tree);
                return (Text) r; // may be null per API
            }
        } catch (Throwable t) {
            if (onError != null) onError.accept(t.toString());
        }

        return null;
    }

    /**
     * Encode Text -> JSON.
     * On 1.20.6+ / 1.21.x uses TextCodecs codec + RegistryOps when provided.
     * On 1.20.1 falls back to Text.Serializer.toJsonTree(Text).
     */
    private static JsonElement encodeText(Text text, RegistryWrapper.WrapperLookup registries) {
        ensureReflectionReady();

        if (textCodecsClass != null && textCodecsCodecMethod != null) {
            try {
                Object codec = textCodecsCodecMethod.invoke(null, maxJsonLength);
                Object ops = (registries != null)
                        ? RegistryOps.of(JsonOps.INSTANCE, registries)
                        : JsonOps.INSTANCE;

                @SuppressWarnings("unchecked")
                DataResult<JsonElement> encoded = (DataResult<JsonElement>) codec.getClass()
                        .getMethod("encodeStart", com.mojang.serialization.DynamicOps.class, Object.class)
                        .invoke(codec, ops, text);

                return encoded.result().orElse(null);
            } catch (Throwable t) {
            }
        }

        try {
            if (textSerializerToJsonTreeMethod != null) {
                return (JsonElement) textSerializerToJsonTreeMethod.invoke(null, text);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static void ensureReflectionReady() {
        if (textCodecsChecked) return;

        synchronized (FabricComponentSerializer.class) {
            if (textCodecsChecked) return;

            try {
                textCodecsClass = Class.forName("net.minecraft.text.TextCodecs");

                try {
                    textCodecsCodecMethod = textCodecsClass.getMethod("withJsonLengthLimit", int.class);
                } catch (NoSuchMethodException ignored) {
                    textCodecsCodecMethod = textCodecsClass.getMethod("codec", int.class);
                }
            } catch (Throwable ignored) {
                textCodecsClass = null;
                textCodecsCodecMethod = null;
            }

            try {
                Class<?> serializer = Class.forName("net.minecraft.text.Text$Serializer");
                textSerializerFromJsonMethod = serializer.getMethod("fromJson", com.google.gson.JsonElement.class);
                textSerializerToJsonTreeMethod = serializer.getMethod("toJsonTree", net.minecraft.text.Text.class);
            } catch (Throwable ignored) {
                textSerializerFromJsonMethod = null;
                textSerializerToJsonTreeMethod = null;
            }

            textCodecsChecked = true;
        }
    }
}
