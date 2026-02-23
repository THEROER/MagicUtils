package dev.ua.theroer.magicutils.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Lightweight helpers for optional runtime integrations via reflection.
 *
 * <p>All operations are safe-by-default and return {@link Optional} values
 * instead of throwing reflective exceptions.</p>
 */
public final class ReflectiveAccess {
    private ReflectiveAccess() {
    }

    public static Optional<Class<?>> loadClass(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Class<?>> loadFirstAvailable(String... classNames) {
        if (classNames == null || classNames.length == 0) {
            return Optional.empty();
        }
        for (String className : classNames) {
            Optional<Class<?>> loaded = loadClass(className);
            if (loaded.isPresent()) {
                return loaded;
            }
        }
        return Optional.empty();
    }

    public static Optional<Method> publicMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            Method method = type.getMethod(name, parameterTypes != null ? parameterTypes : new Class<?>[0]);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Method> firstMethod(
            Class<?> type,
            Predicate<Method> predicate
    ) {
        if (type == null || predicate == null) {
            return Optional.empty();
        }
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (predicate.test(method)) {
                method.setAccessible(true);
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    public static Optional<Field> publicField(Class<?> type, String name) {
        if (type == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            Field field = type.getField(name);
            field.setAccessible(true);
            return Optional.of(field);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Field> firstPublicStaticField(
            Class<?> type,
            Predicate<Field> predicate
    ) {
        if (type == null || predicate == null) {
            return Optional.empty();
        }
        Field[] fields = type.getFields();
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                continue;
            }
            if (predicate.test(field)) {
                field.setAccessible(true);
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    public static Optional<Object> readField(Field field, Object target) {
        if (field == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Object> invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(method.invoke(target, args != null ? args : new Object[0]));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> cast(Object value, Class<T> expectedType) {
        if (value == null || expectedType == null || !expectedType.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of(expectedType.cast(value));
    }

    public static Predicate<Method> signature(
            String name,
            Class<?> returnType,
            Class<?>... params
    ) {
        return method -> {
            if (method == null) {
                return false;
            }
            if (name != null && !name.equals(method.getName())) {
                return false;
            }
            if (returnType != null && method.getReturnType() != returnType) {
                return false;
            }
            return Arrays.equals(method.getParameterTypes(), params != null ? params : new Class<?>[0]);
        };
    }
}
