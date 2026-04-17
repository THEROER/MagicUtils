package dev.ua.theroer.magicutils.platform.fabric.codec;

import com.google.gson.JsonElement;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

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

    /**
     * Creates a new instance of LegacyTextSerializerAdapter.
     * Searches for Text serializer classes and reflective methods for JSON conversion.
     * 
     * @throws IllegalStateException if the serializer class or methods are not found
     */
    public LegacyTextSerializerAdapter() {
        Class<?> serializerClass = ReflectiveAccess.loadFirstAvailable(SERIALIZER_CLASS_NAMES)
                .orElseThrow(() -> new IllegalStateException("Legacy Text serializer class not found"));
        this.resolvedClassName = serializerClass.getName();

        this.fromJsonMethod = ReflectiveAccess.publicMethod(serializerClass, "fromJson", JsonElement.class)
                .or(() -> ReflectiveAccess.publicMethod(serializerClass, "method_10872", JsonElement.class))
                .or(() -> ReflectiveAccess.firstMethod(serializerClass, LegacyTextSerializerAdapter::isFromJsonSignature))
                .orElseThrow(() -> new IllegalStateException("fromJson(JsonElement) static method not found in " + resolvedClassName));

        this.toJsonTreeMethod = ReflectiveAccess.publicMethod(serializerClass, "toJsonTree", Component.class)
                .or(() -> ReflectiveAccess.publicMethod(serializerClass, "method_10868", Component.class))
                .or(() -> ReflectiveAccess.firstMethod(serializerClass, LegacyTextSerializerAdapter::isToJsonTreeSignature))
                .orElseThrow(() -> new IllegalStateException("toJsonTree(Text) static method not found in " + resolvedClassName));
    }

    private static boolean isFromJsonSignature(Method method) {
        if (method == null || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
            return false;
        }
        Class<?> paramType = method.getParameterTypes()[0];
        return JsonElement.class.isAssignableFrom(paramType)
                && Component.class.isAssignableFrom(method.getReturnType());
    }

    private static boolean isToJsonTreeSignature(Method method) {
        if (method == null || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
            return false;
        }
        Class<?> paramType = method.getParameterTypes()[0];
        return paramType.isAssignableFrom(Component.class)
                && JsonElement.class.isAssignableFrom(method.getReturnType());
    }

    @Override
    public Component decode(JsonElement tree, Consumer<String> onError) {
        if (tree == null) {
            return null;
        }
        try {
            Object decoded = fromJsonMethod.invoke(null, tree);
            return (decoded instanceof Component text) ? text : null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to decode Text via " + resolvedClassName, error);
        }
    }

    @Override
    public JsonElement encode(Component text, HolderLookup.Provider registries) {
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
