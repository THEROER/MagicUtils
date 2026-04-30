package dev.ua.theroer.magicutils.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Lightweight helpers for optional runtime integrations via reflection.
 *
 * <p>All operations are safe-by-default and return {@link Optional} values
 * instead of throwing reflective exceptions.</p>
 */
public final class ReflectiveAccess {
    private static final List<NameResolver> NAME_RESOLVERS = new CopyOnWriteArrayList<>();

    private ReflectiveAccess() {
    }

    /**
     * Maps source names from an external namespace to names visible in the current runtime.
     */
    public interface NameResolver {
        /**
         * Maps a class name from the supplied namespace.
         *
         * @param namespace source namespace for the provided name
         * @param className fully qualified source class name
         * @return mapped runtime class name, or empty when unsupported
         */
        default Optional<String> mapClassName(String namespace, String className) {
            return Optional.empty();
        }

        /**
         * Maps a method name from the supplied namespace.
         *
         * @param namespace source namespace for the provided name
         * @param ownerClassName fully qualified source owner class name
         * @param methodName source method name
         * @param descriptor JVM descriptor in the source namespace, when available
         * @return mapped runtime method name, or empty when unsupported
         */
        default Optional<String> mapMethodName(
                String namespace,
                String ownerClassName,
                String methodName,
                String descriptor
        ) {
            return Optional.empty();
        }

        /**
         * Maps a field name from the supplied namespace.
         *
         * @param namespace source namespace for the provided name
         * @param ownerClassName fully qualified source owner class name
         * @param fieldName source field name
         * @param descriptor JVM descriptor in the source namespace, when available
         * @return mapped runtime field name, or empty when unsupported
         */
        default Optional<String> mapFieldName(
                String namespace,
                String ownerClassName,
                String fieldName,
                String descriptor
        ) {
            return Optional.empty();
        }
    }

    /**
     * Registers a runtime name resolver used by mapped lookup helpers.
     *
     * @param resolver resolver to add
     */
    public static void registerNameResolver(NameResolver resolver) {
        if (resolver != null && !NAME_RESOLVERS.contains(resolver)) {
            NAME_RESOLVERS.add(resolver);
        }
    }

    /**
     * Attempts to load a class by its fully qualified name.
     *
     * @param className the name of the class to load
     * @return an Optional containing the class if found, otherwise empty
     */
    public static Optional<Class<?>> loadClass(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        Optional<Class<?>> contextClass = loadClass(className, Thread.currentThread().getContextClassLoader());
        if (contextClass.isPresent()) {
            return contextClass;
        }
        return loadClass(className, ReflectiveAccess.class.getClassLoader());
    }

    private static Optional<Class<?>> loadClass(String className, ClassLoader loader) {
        if (loader == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(className, false, loader));
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to load a class directly and then through registered name resolvers.
     *
     * @param className class name to load
     * @return an Optional containing the class if found, otherwise empty
     */
    public static Optional<Class<?>> loadMappedClass(String className) {
        return loadMappedClass(null, className);
    }

    /**
     * Attempts to load a class directly and then through registered name resolvers.
     *
     * @param namespace source namespace for mapped lookup
     * @param className class name to load
     * @return an Optional containing the class if found, otherwise empty
     */
    public static Optional<Class<?>> loadMappedClass(String namespace, String className) {
        Optional<Class<?>> direct = loadClass(className);
        if (direct.isPresent()) {
            return direct;
        }
        for (NameResolver resolver : NAME_RESOLVERS) {
            Optional<Class<?>> mapped = mapClassName(resolver, namespace, className)
                    .filter(name -> !name.equals(className))
                    .flatMap(ReflectiveAccess::loadClass);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to load the first available class from a list of names.
     * Useful for handling different environment-specific class names (e.g., Yarn vs Mojang mappings).
     *
     * @param classNames list of class names to try
     * @return an Optional containing the first successfully loaded class, otherwise empty
     */
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

    /**
     * Attempts to find a public method in the given type.
     * The method is automatically made accessible.
     *
     * @param type the class to search in
     * @param name the name of the method
     * @param parameterTypes the parameter types of the method
     * @return an Optional containing the method if found, otherwise empty
     */
    public static Optional<Method> publicMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            Method method = type.getMethod(name, parameterTypes != null ? parameterTypes : new Class<?>[0]);
            method.setAccessible(true);
            return Optional.of(method);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to find a public method directly and then through registered name resolvers.
     *
     * @param type the class to search in
     * @param name method name to find
     * @param parameterTypes the parameter types of the method
     * @return an Optional containing the method if found, otherwise empty
     */
    public static Optional<Method> publicMappedMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        return publicMappedMethod(type, null, type != null ? type.getName() : null, name, null, parameterTypes);
    }

    /**
     * Attempts to find a public method directly and then through registered name resolvers.
     *
     * @param type the class to search in
     * @param namespace source namespace for mapped lookup
     * @param ownerClassName fully qualified source owner class name
     * @param methodName method name to find
     * @param descriptor JVM method descriptor in the source namespace, when available
     * @param parameterTypes runtime parameter types of the method
     * @return an Optional containing the method if found, otherwise empty
     */
    public static Optional<Method> publicMappedMethod(
            Class<?> type,
            String namespace,
            String ownerClassName,
            String methodName,
            String descriptor,
            Class<?>... parameterTypes
    ) {
        Optional<Method> direct = publicMethod(type, methodName, parameterTypes);
        if (direct.isPresent()) {
            return direct;
        }
        for (NameResolver resolver : NAME_RESOLVERS) {
            Optional<Method> mapped = mapMethodName(resolver, namespace, ownerClassName, methodName, descriptor)
                    .filter(name -> !name.equals(methodName))
                    .flatMap(name -> publicMethod(type, name, parameterTypes));
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the first public method in the given type that matches the predicate.
     *
     * @param type the class to search in
     * @param predicate the condition to match
     * @return an Optional containing the first matching method, otherwise empty
     */
    public static Optional<Method> firstMethod(
            Class<?> type,
            Predicate<Method> predicate
    ) {
        if (type == null || predicate == null) {
            return Optional.empty();
        }
        try {
            Method[] methods = type.getMethods();
            for (Method method : methods) {
                if (predicate.test(method)) {
                    method.setAccessible(true);
                    return Optional.of(method);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Attempts to find a public field in the given type by its name.
     * The field is automatically made accessible.
     *
     * @param type the class to search in
     * @param name the name of the field
     * @return an Optional containing the field if found, otherwise empty
     */
    public static Optional<Field> publicField(Class<?> type, String name) {
        if (type == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            Field field = type.getField(name);
            field.setAccessible(true);
            return Optional.of(field);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to find a public field directly and then through registered name resolvers.
     *
     * @param type the class to search in
     * @param name field name to find
     * @return an Optional containing the field if found, otherwise empty
     */
    public static Optional<Field> publicMappedField(Class<?> type, String name) {
        return publicMappedField(type, null, type != null ? type.getName() : null, name, null);
    }

    /**
     * Attempts to find a public field directly and then through registered name resolvers.
     *
     * @param type the class to search in
     * @param namespace source namespace for mapped lookup
     * @param ownerClassName fully qualified source owner class name
     * @param fieldName field name to find
     * @param descriptor JVM field descriptor in the source namespace, when available
     * @return an Optional containing the field if found, otherwise empty
     */
    public static Optional<Field> publicMappedField(
            Class<?> type,
            String namespace,
            String ownerClassName,
            String fieldName,
            String descriptor
    ) {
        Optional<Field> direct = publicField(type, fieldName);
        if (direct.isPresent()) {
            return direct;
        }
        for (NameResolver resolver : NAME_RESOLVERS) {
            Optional<Field> mapped = mapFieldName(resolver, namespace, ownerClassName, fieldName, descriptor)
                    .filter(name -> !name.equals(fieldName))
                    .flatMap(name -> publicField(type, name));
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the first public static field in the given type that matches the predicate.
     *
     * @param type the class to search in
     * @param predicate the condition to match
     * @return an Optional containing the first matching field, otherwise empty
     */
    public static Optional<Field> firstPublicStaticField(
            Class<?> type,
            Predicate<Field> predicate
    ) {
        if (type == null || predicate == null) {
            return Optional.empty();
        }
        try {
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
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Reads the value of a field from a target object.
     *
     * @param field the field to read
     * @param target the object instance (null for static fields)
     * @return an Optional containing the field value if read successfully, otherwise empty
     */
    public static Optional<Object> readField(Field field, Object target) {
        if (field == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    /**
     * Invokes a method on a target object with the given arguments.
     *
     * @param method the method to invoke
     * @param target the object instance (null for static methods)
     * @param args the arguments for the invocation
     * @return an Optional containing the invocation result if successful, otherwise empty
     */
    public static Optional<Object> invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(method.invoke(target, args != null ? args : new Object[0]));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    /**
     * Casts an object to the expected type safely.
     * Handles primitive-to-wrapper conversions.
     *
     * @param value the object to cast
     * @param expectedType the class to cast to
     * @param <T> the target type
     * @return an Optional containing the casted value if successful, otherwise empty
     */
    public static <T> Optional<T> cast(Object value, Class<T> expectedType) {
        if (value == null || expectedType == null) {
            return Optional.empty();
        }
        Class<?> targetType = expectedType.isPrimitive() ? wrapPrimitive(expectedType) : expectedType;
        if (targetType == null || !targetType.isInstance(value)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T castValue = (T) targetType.cast(value);
        return Optional.of(castValue);
    }

    /**
     * Creates a predicate to match a method signature.
     *
     * @param name the method name (null to ignore)
     * @param returnType the return type (null to ignore)
     * @param params the parameter types
     * @return a predicate for method matching
     */
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

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Optional<String> mapClassName(NameResolver resolver, String namespace, String className) {
        if (resolver == null || className == null || className.isBlank()) {
            return Optional.empty();
        }
        try {
            return resolver.mapClassName(namespace, className).filter(name -> !name.isBlank());
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> mapMethodName(
            NameResolver resolver,
            String namespace,
            String ownerClassName,
            String methodName,
            String descriptor
    ) {
        if (resolver == null || ownerClassName == null || methodName == null || methodName.isBlank()) {
            return Optional.empty();
        }
        try {
            return resolver.mapMethodName(namespace, ownerClassName, methodName, descriptor)
                    .filter(name -> !name.isBlank());
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> mapFieldName(
            NameResolver resolver,
            String namespace,
            String ownerClassName,
            String fieldName,
            String descriptor
    ) {
        if (resolver == null || ownerClassName == null || fieldName == null || fieldName.isBlank()) {
            return Optional.empty();
        }
        try {
            return resolver.mapFieldName(namespace, ownerClassName, fieldName, descriptor)
                    .filter(name -> !name.isBlank());
        } catch (RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
