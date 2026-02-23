package dev.ua.theroer.magicutils.platform.fabric.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Strict adapter for modern Fabric/Yarn where TextCodecs codec exists.
 */
public final class YarnTextCodecsAdapter implements TextSerializationAdapter {
    private static final String[] CODEC_CLASS_NAMES = {
            "net.minecraft.text.TextCodecs",
            "net.minecraft.class_8824"
    };

    private final Object codec;
    private final Method parseMethod;
    private final Method encodeStartMethod;
    private final String resolvedClassName;

    public YarnTextCodecsAdapter() {
        Class<?> codecClass = ReflectiveAccess.loadFirstAvailable(CODEC_CLASS_NAMES)
                .orElseThrow(() -> new IllegalStateException("TextCodecs class not found"));
        this.resolvedClassName = codecClass.getName();

        Field codecField = ReflectiveAccess.publicField(codecClass, "CODEC")
                .or(() -> ReflectiveAccess.firstPublicStaticField(codecClass, field ->
                        field.getType().getName().contains("Codec")
                ))
                .orElseThrow(() -> new IllegalStateException("Text codec field not found in " + resolvedClassName));

        this.codec = ReflectiveAccess.readField(codecField, null)
                .orElseThrow(() -> new IllegalStateException("Unable to read codec field from " + resolvedClassName));

        Class<?> codecType = codec.getClass();
        this.parseMethod = ReflectiveAccess.publicMethod(codecType, "parse", DynamicOps.class, Object.class)
                .orElseThrow(() -> new IllegalStateException("Codec.parse(DynamicOps,Object) not found in " + codecType.getName()));
        this.encodeStartMethod = ReflectiveAccess.publicMethod(codecType, "encodeStart", DynamicOps.class, Object.class)
                .orElseThrow(() -> new IllegalStateException("Codec.encodeStart(DynamicOps,Object) not found in " + codecType.getName()));
    }

    @Override
    public Text decode(JsonElement tree, Consumer<String> onError) {
        if (tree == null) {
            return null;
        }
        DataResult<Text> result;
        try {
            @SuppressWarnings("unchecked")
            DataResult<Text> parsed = (DataResult<Text>) parseMethod.invoke(codec, JsonOps.INSTANCE, tree);
            result = parsed;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to parse Text via codec " + resolvedClassName, error);
        }

        if (onError != null) {
            result.error().ifPresent(error -> onError.accept(error.message()));
        }
        return result.result().orElse(null);
    }

    @Override
    public JsonElement encode(Text text, RegistryWrapper.WrapperLookup registries) {
        if (text == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        DynamicOps<JsonElement> ops = registries != null
                ? (DynamicOps<JsonElement>) RegistryOps.of(JsonOps.INSTANCE, registries)
                : JsonOps.INSTANCE;
        DataResult<JsonElement> result;
        try {
            @SuppressWarnings("unchecked")
            DataResult<JsonElement> encoded = (DataResult<JsonElement>) encodeStartMethod.invoke(codec, ops, text);
            result = encoded;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to encode Text via codec " + resolvedClassName, error);
        }
        return result.result().orElse(null);
    }

    @Override
    public String name() {
        return resolvedClassName + ".CODEC";
    }
}
