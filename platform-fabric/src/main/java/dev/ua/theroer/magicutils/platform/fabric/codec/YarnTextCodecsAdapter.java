package dev.ua.theroer.magicutils.platform.fabric.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;

/**
 * Strict adapter for modern Fabric runtimes where a codec-based component serializer exists.
 */
public final class YarnTextCodecsAdapter implements TextSerializationAdapter {
    private static final String[] CODEC_CLASS_NAMES = {
            "net.minecraft.network.chat.ComponentSerialization",
            "net.minecraft.text.TextCodecs",
            "net.minecraft.class_8824"
    };

    private final Object codec;
    private final Method parseMethod;
    private final Method encodeStartMethod;
    private final String resolvedClassName;

    /**
     * Creates a new instance of YarnTextCodecsAdapter.
     * Reflectively locates and accesses the modern TextCodecs.CODEC.
     *
     * @throws IllegalStateException if the codec class or its fields/methods are not found
     */
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
    public Component decode(JsonElement tree, Consumer<String> onError) {
        if (tree == null) {
            return null;
        }
        Object result;
        try {
            result = parseMethod.invoke(codec, JsonOps.INSTANCE, tree);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to parse Text via codec " + resolvedClassName, error);
        }

        reportError(result, onError);
        return dataResultValue(result, Component.class).orElse(null);
    }

    @Override
    public JsonElement encode(Component text, HolderLookup.Provider registries) {
        if (text == null) {
            return null;
        }
        DynamicOps<JsonElement> ops = registries != null
                ? (DynamicOps<JsonElement>) RegistryOps.create(JsonOps.INSTANCE, registries)
                : JsonOps.INSTANCE;
        Object result;
        try {
            result = encodeStartMethod.invoke(codec, ops, text);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to encode Text via codec " + resolvedClassName, error);
        }
        return dataResultValue(result, JsonElement.class).orElse(null);
    }

    @Override
    public String name() {
        return resolvedClassName + ".CODEC";
    }

    private static void reportError(Object dataResult, Consumer<String> onError) {
        if (onError == null) {
            return;
        }
        dataResultOptional(dataResult, "error")
                .ifPresent(error -> onError.accept(dataResultErrorMessage(error)));
    }

    private static <T> Optional<T> dataResultValue(Object dataResult, Class<T> expectedType) {
        return dataResultOptional(dataResult, "result")
                .flatMap(value -> ReflectiveAccess.cast(value, expectedType));
    }

    private static Optional<Object> dataResultOptional(Object dataResult, String methodName) {
        if (dataResult == null) {
            return Optional.empty();
        }
        Optional<Method> method = ReflectiveAccess.publicMethod(dataResult.getClass(), methodName);
        if (method.isEmpty()) {
            return Optional.empty();
        }
        Optional<Object> invoked = ReflectiveAccess.invoke(method.get(), dataResult);
        if (invoked.isEmpty() || !(invoked.get() instanceof Optional<?> optional)) {
            return Optional.empty();
        }
        return optional.map(Object.class::cast);
    }

    private static String dataResultErrorMessage(Object error) {
        if (error == null) {
            return "unknown codec error";
        }
        Optional<Method> messageMethod = ReflectiveAccess.publicMethod(error.getClass(), "message");
        return messageMethod
                .flatMap(method -> ReflectiveAccess.invoke(method, error))
                .map(String::valueOf)
                .orElseGet(error::toString);
    }
}
