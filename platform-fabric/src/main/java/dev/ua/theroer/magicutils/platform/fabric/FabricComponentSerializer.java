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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("MagicUtils-Fabric-Serializer");
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int DEFAULT_MAX_JSON_LENGTH = 262_144;

    private static volatile int maxJsonLength = DEFAULT_MAX_JSON_LENGTH;

    // Reflection caches (loaded lazily)
    private static volatile boolean textCodecsChecked = false;
    private static volatile Class<?> textCodecsClass;
    private static volatile Object textCodec; 
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

        try {
            JsonElement tree = GSON.serializeToTree(component);
            Text decoded = decodeText(tree, onError);
            if (decoded != null) return decoded;
        } catch (Exception e) {
            if (onError != null) onError.accept(e.toString());
        }
        
        return Text.literal(PLAIN.serialize(component));
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
            if (tree != null) {
                return GSON.deserializeFromTree(tree);
            }
        } catch (Exception e) {
            // fallback
        }
        return Component.text(Objects.toString(text.getString(), ""));
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
     */
    private static Text decodeText(JsonElement tree, Consumer<String> onError) {
        ensureReflectionReady();

        if (textCodec != null) {
            try {
                @SuppressWarnings("unchecked")
                DataResult<Text> result = (DataResult<Text>) textCodec.getClass()
                        .getMethod("parse", com.mojang.serialization.DynamicOps.class, Object.class)
                        .invoke(textCodec, JsonOps.INSTANCE, tree);

                if (onError != null) result.error().ifPresent(err -> onError.accept(err.message()));
                return result.result().orElse(null);
            } catch (Throwable t) {
                if (onError != null) onError.accept(t.toString());
            }
        }

        try {
            if (textSerializerFromJsonMethod != null) {
                return (Text) textSerializerFromJsonMethod.invoke(null, tree);
            }
        } catch (Throwable t) {
            if (onError != null) onError.accept(t.toString());
        }

        return null;
    }

    /**
     * Encode Text -> JSON.
     */
    private static JsonElement encodeText(Text text, RegistryWrapper.WrapperLookup registries) {
        ensureReflectionReady();

        if (textCodec != null) {
            try {
                Object ops = (registries != null)
                        ? RegistryOps.of(JsonOps.INSTANCE, registries)
                        : JsonOps.INSTANCE;

                @SuppressWarnings("unchecked")
                DataResult<JsonElement> encoded = (DataResult<JsonElement>) textCodec.getClass()
                        .getMethod("encodeStart", com.mojang.serialization.DynamicOps.class, Object.class)
                        .invoke(textCodec, ops, text);

                return encoded.result().orElse(null);
            } catch (Throwable t) {
                // ignore
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

            // Try 1.20.5+ TextCodecs (net.minecraft.class_9160)
            String[] codecClassNames = {"net.minecraft.text.TextCodecs", "net.minecraft.class_9160"};
            for (String name : codecClassNames) {
                try {
                    textCodecsClass = Class.forName(name);
                    LOGGER.info("Scanning class for Codecs: {}", name);
                    
                    Field[] fields = textCodecsClass.getFields();
                    for (Field f : fields) {
                        String typeName = f.getType().getName();
                        LOGGER.info("  Field: {} Type: {}", f.getName(), typeName);
                        
                        // Look for a Codec field
                        if (typeName.contains("Codec") || typeName.contains("class_7243") || typeName.contains("serialization.Codec")) {
                            try {
                                Object value = f.get(null);
                                if (value != null) {
                                    textCodec = value;
                                    LOGGER.info("Found potential codec in field: {} ({})", f.getName(), typeName);
                                    // Usually the first public static Codec is the main one
                                    if (f.getName().equals("CODEC") || f.getName().equals("field_48769") || f.getName().equals("field_49651")) {
                                        break; 
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (textCodec != null) break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try 1.21+ Serialization class or 1.20.1 Serializer inner class
            try {
                Class<?> serializationClass = null;
                String[] classNames = {
                    "net.minecraft.text.Text$Serialization", // 1.21+ yarn
                    "net.minecraft.class_2561$class_9821",   // 1.21+ intermediary
                    "net.minecraft.text.Text$Serializer",    // <1.21 yarn
                    "net.minecraft.class_2561$class_2562"    // <1.21 intermediary
                };

                for (String name : classNames) {
                    try {
                        serializationClass = Class.forName(name);
                        if (serializationClass != null) {
                            LOGGER.info("Scanning serialization class: {}", name);
                            break;
                        }
                    } catch (ClassNotFoundException ignored) {}
                }

                if (serializationClass != null) {
                    Method[] methods = serializationClass.getDeclaredMethods();
                    LOGGER.info("Dumping methods for {}", serializationClass.getName());
                    for (Method m : methods) {
                        Class<?>[] params = m.getParameterTypes();
                        Class<?> returnType = m.getReturnType();
                        LOGGER.info("  Method: {} Return: {} Params: {}", m.getName(), returnType.getName(), java.util.Arrays.toString(params));
                        
                        // fromJson: takes 1 param (JsonElement or descendant), returns Text (class_2561)
                        if (params.length == 1 && (params[0].getSimpleName().contains("JsonElement") || params[0].getName().contains("class_3127"))) {
                            if (returnType.getSimpleName().equals("Text") || returnType.getName().contains("class_2561")) {
                                textSerializerFromJsonMethod = m;
                                LOGGER.info("Identified fromJson method: {}", m.getName());
                            }
                        }
                        // toJsonTree: takes 1 param (Text), returns JsonElement
                        else if (params.length == 1 && (params[0].getSimpleName().equals("Text") || params[0].getName().contains("class_2561"))) {
                            if (returnType.getSimpleName().contains("JsonElement") || returnType.getName().contains("class_3127")) {
                                textSerializerToJsonTreeMethod = m;
                                LOGGER.info("Identified toJsonTree method: {}", m.getName());
                            }
                        }
                    }
                }

                if (textSerializerFromJsonMethod != null) {
                    LOGGER.info("Initialized Text serialization via {} -> {}", 
                        serializationClass.getSimpleName(), textSerializerFromJsonMethod.getName());
                }
            } catch (Throwable t) {
                LOGGER.warn("Error during serialization method scan: {}", t.toString());
            }

            if (textCodec == null && textSerializerFromJsonMethod == null) {
                LOGGER.error("CRITICAL: No text serialization methods found! Formatting will be disabled.");
            }

            textCodecsChecked = true;
        }
    }
}
