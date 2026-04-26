package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

final class FabricIdentifierBridge {
    private static final List<String> IDENTIFIER_CLASS_NAMES = List.of(
            "net.minecraft.resources.Identifier",
            "net.minecraft.resources.ResourceLocation"
    );
    private static final List<String> CREATE_METHOD_NAMES = List.of(
            "fromNamespaceAndPath",
            "of"
    );
    private static final List<String> PARSE_METHOD_NAMES = List.of(
            "tryParse",
            "parse",
            "of"
    );

    private static final Class<?> IDENTIFIER_TYPE = ReflectiveAccess
            .loadFirstAvailable(IDENTIFIER_CLASS_NAMES.toArray(String[]::new))
            .orElse(null);
    private static final Method CREATE_METHOD = resolveFactory(CREATE_METHOD_NAMES, String.class, String.class);
    private static final Method PARSE_METHOD = resolveFactory(PARSE_METHOD_NAMES, String.class);
    private static final Constructor<?> PAIR_CONSTRUCTOR = resolveConstructor(String.class, String.class);
    private static final Constructor<?> STRING_CONSTRUCTOR = resolveConstructor(String.class);

    private FabricIdentifierBridge() {
    }

    static Class<?> type() {
        return IDENTIFIER_TYPE;
    }

    static Object create(String namespace, String path) {
        if (IDENTIFIER_TYPE == null || isBlank(namespace) || isBlank(path)) {
            return null;
        }
        if (CREATE_METHOD != null) {
            Object created = ReflectiveAccess.invoke(CREATE_METHOD, null, namespace, path).orElse(null);
            if (created != null) {
                return created;
            }
        }
        if (PAIR_CONSTRUCTOR != null) {
            Object created = instantiate(PAIR_CONSTRUCTOR, namespace, path);
            if (created != null) {
                return created;
            }
        }
        return parse(namespace + ":" + path);
    }

    static Object parse(String value) {
        if (IDENTIFIER_TYPE == null || isBlank(value)) {
            return null;
        }
        if (PARSE_METHOD != null) {
            Object parsed = ReflectiveAccess.invoke(PARSE_METHOD, null, value).orElse(null);
            if (parsed != null) {
                return parsed;
            }
        }
        return STRING_CONSTRUCTOR != null ? instantiate(STRING_CONSTRUCTOR, value) : null;
    }

    private static Method resolveFactory(List<String> preferredNames, Class<?>... parameterTypes) {
        if (IDENTIFIER_TYPE == null) {
            return null;
        }
        for (String name : preferredNames) {
            Method method = ReflectiveAccess.publicMethod(IDENTIFIER_TYPE, name, parameterTypes).orElse(null);
            if (isFactoryMethod(method, parameterTypes)) {
                return method;
            }
        }
        for (Method method : IDENTIFIER_TYPE.getMethods()) {
            if (isFactoryMethod(method, parameterTypes)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isFactoryMethod(Method method, Class<?>... parameterTypes) {
        if (method == null || IDENTIFIER_TYPE == null) {
            return false;
        }
        return Modifier.isStatic(method.getModifiers())
                && IDENTIFIER_TYPE.isAssignableFrom(method.getReturnType())
                && Arrays.equals(method.getParameterTypes(), parameterTypes);
    }

    private static Constructor<?> resolveConstructor(Class<?>... parameterTypes) {
        if (IDENTIFIER_TYPE == null) {
            return null;
        }
        for (Constructor<?> constructor : IDENTIFIER_TYPE.getConstructors()) {
            if (!Arrays.equals(constructor.getParameterTypes(), parameterTypes)) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static Object instantiate(Constructor<?> constructor, Object... args) {
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
