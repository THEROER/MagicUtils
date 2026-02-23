package dev.ua.theroer.magicutils.platform.fabric.codec;

import com.google.gson.JsonElement;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Strict adapter for legacy versions where Text serializer exposes static
 * fromJson/toJsonTree methods (for example 1.20.x).
 */
public final class LegacyTextSerializerAdapter implements TextSerializationAdapter {
    private static final String[] SERIALIZER_CLASS_NAMES = {
            "net.minecraft.text.Text$Serializer",
            "net.minecraft.class_2561$class_2562"
    };

    private final Method fromJsonMethod;
    private final Method toJsonTreeMethod;
    private final String resolvedClassName;

    public LegacyTextSerializerAdapter() {
        Class<?> serializerClass = ReflectiveAccess.loadFirstAvailable(SERIALIZER_CLASS_NAMES)
                .orElseThrow(() -> new IllegalStateException("Legacy Text serializer class not found"));
        this.resolvedClassName = serializerClass.getName();

        this.fromJsonMethod = ReflectiveAccess.publicMethod(serializerClass, "fromJson", JsonElement.class)
                .or(() -> ReflectiveAccess.publicMethod(serializerClass, "method_10872", JsonElement.class))
                .or(() -> ReflectiveAccess.firstMethod(serializerClass, LegacyTextSerializerAdapter::isFromJsonSignature))
                .orElseThrow(() -> new IllegalStateException("fromJson(JsonElement) static method not found in " + resolvedClassName));

        this.toJsonTreeMethod = ReflectiveAccess.publicMethod(serializerClass, "toJsonTree", Text.class)
                .or(() -> ReflectiveAccess.publicMethod(serializerClass, "method_10868", Text.class))
                .or(() -> ReflectiveAccess.firstMethod(serializerClass, LegacyTextSerializerAdapter::isToJsonTreeSignature))
                .orElseThrow(() -> new IllegalStateException("toJsonTree(Text) static method not found in " + resolvedClassName));
    }

    private static boolean isFromJsonSignature(Method method) {
        if (method == null || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
            return false;
        }
        Class<?> paramType = method.getParameterTypes()[0];
        return JsonElement.class.isAssignableFrom(paramType)
                && Text.class.isAssignableFrom(method.getReturnType());
    }

    private static boolean isToJsonTreeSignature(Method method) {
        if (method == null || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
            return false;
        }
        Class<?> paramType = method.getParameterTypes()[0];
        return paramType.isAssignableFrom(Text.class)
                && JsonElement.class.isAssignableFrom(method.getReturnType());
    }

    @Override
    public Text decode(JsonElement tree, Consumer<String> onError) {
        if (tree == null) {
            return null;
        }
        try {
            Object decoded = fromJsonMethod.invoke(null, tree);
            return (decoded instanceof Text text) ? text : null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to decode Text via " + resolvedClassName, error);
        }
    }

    @Override
    public JsonElement encode(Text text, RegistryWrapper.WrapperLookup registries) {
        if (text == null) {
            return null;
        }
        try {
            Object encoded = toJsonTreeMethod.invoke(null, text);
            return (encoded instanceof JsonElement json) ? json : null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to encode Text via " + resolvedClassName, error);
        }
    }

    @Override
    public String name() {
        return resolvedClassName;
    }
}
