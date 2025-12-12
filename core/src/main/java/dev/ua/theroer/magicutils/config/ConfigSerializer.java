package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.ConfigSerializable;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Handles serialization and deserialization of complex config objects.
 */
public class ConfigSerializer {
    /**
     * Private constructor to prevent instantiation.
     */
    private ConfigSerializer() {
    }

    /**
     * Serializes an object to a map for YAML.
     * 
     * @param obj the object to serialize
     * @return the serialized map representation
     */
    public static Map<String, Object> serialize(Object obj) {
        if (obj == null)
            return null;

        Map<String, Object> result = new LinkedHashMap<>();
        Class<?> clazz = obj.getClass();

        // Check if class is serializable
        ConfigSerializable serializable = clazz.getAnnotation(ConfigSerializable.class);
        boolean includeNulls = serializable != null && serializable.includeNulls();

        // Process all fields
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            // Skip transient fields
            if (java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            try {
                Object value = field.get(obj);

                // Skip nulls if configured
                if (value == null && !includeNulls)
                    continue;

                String key = resolveKey(field);

                // Handle different types
                if (value == null) {
                    result.put(key, null);
                } else if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                    result.put(key, value);
                } else if (value instanceof List) {
                    Class<?> elementType = extractListElementType(field);
                    result.put(key, serializeList((List<?>) value, elementType));
                } else if (value instanceof Map) {
                    Class<?> valueType = extractMapValueType(field);
                    result.put(key, serializeMap((Map<?, ?>) value, valueType));
                } else if (value.getClass().isAnnotationPresent(ConfigSerializable.class)) {
                    result.put(key, serialize(value));
                } else {
                    ConfigValueAdapter<?> adapter = ConfigAdapters.get(field.getType());
                    if (adapter == null) {
                        adapter = ConfigAdapters.get(value.getClass());
                    }
                    if (adapter != null) {
                        @SuppressWarnings("unchecked")
                        ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                        result.put(key, typed.serialize(value));
                    } else {
                        // Try toString for other types
                        result.put(key, value.toString());
                    }
                }

            } catch (IllegalAccessException e) {
                // Skip field
            }
        }

        return result;
    }

    /**
     * Deserializes a map to an object.
     * 
     * @param <T>   the type to deserialize to
     * @param data  the map data to deserialize
     * @param clazz the class to deserialize to
     * @return the deserialized object
     * @throws SecurityException if the class is not marked as @ConfigSerializable
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Map<String, Object> data, Class<T> clazz) {
        // Security check: only allow deserialization of classes marked with @ConfigSerializable
        if (!clazz.isAnnotationPresent(ConfigSerializable.class)) {
            throw new SecurityException(
                "Deserialization is only allowed for classes annotated with @ConfigSerializable. " +
                "Class '" + clazz.getName() + "' is not marked as serializable."
            );
        }
        
        // Additional security: check package to prevent deserialization of system classes
        String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
        if (packageName.startsWith("java.") || 
            packageName.startsWith("javax.") || 
            packageName.startsWith("sun.") ||
            packageName.startsWith("com.sun.")) {
            throw new SecurityException(
                "Deserialization of system classes is not allowed. " +
                "Attempted to deserialize: " + clazz.getName()
            );
        }
        
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();

            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);

                String key = resolveKey(field);
                if (!data.containsKey(key))
                    continue;

                Object value = data.get(key);
                if (value == null) {
                    field.set(instance, null);
                    continue;
                }

                try {
                    Class<?> fieldType = field.getType();

                    // Handle different types
                    ConfigValueAdapter<?> adapter = ConfigAdapters.get(fieldType);
                    if (adapter != null) {
                        ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                        field.set(instance, typed.deserialize(value));
                    } else if (isPrimitiveOrWrapper(fieldType) || fieldType == String.class) {
                        field.set(instance, convertValue(value, fieldType));
                    } else if (List.class.isAssignableFrom(fieldType) && value instanceof List) {
                        ParameterizedType listType = (ParameterizedType) field.getGenericType();
                        Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];
                        field.set(instance, deserializeList((List<?>) value, elementType));
                    } else if (Map.class.isAssignableFrom(fieldType) && value instanceof Map) {
                        ParameterizedType mapType = (ParameterizedType) field.getGenericType();
                        Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];
                        field.set(instance, deserializeMap((Map<?, ?>) value, valueType));
                    } else if (fieldType.isAnnotationPresent(ConfigSerializable.class) && value instanceof Map) {
                        // Recursive deserialization - security check is already in deserialize method
                        field.set(instance, deserialize((Map<String, Object>) value, fieldType));
                    }
                } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException e) {
                    // Skip field
                }
            }

            return instance;

        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException e) {
            throw new RuntimeException("Failed to deserialize " + clazz.getName(), e);
        }
    }

    /**
     * Serializes a list.
     */
    private static List<Object> serializeList(List<?> list, Class<?> elementType) {
        List<Object> result = new ArrayList<>();

        for (Object item : list) {
            if (item == null) {
                result.add(null);
            } else if (elementType != null) {
                ConfigValueAdapter<?> adapter = ConfigAdapters.get(elementType);
                if (adapter != null) {
                    @SuppressWarnings("unchecked")
                    ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                    result.add(typed.serialize(item));
                } else if (elementType.isAnnotationPresent(ConfigSerializable.class) && !(item instanceof Map)) {
                    result.add(serialize(item));
                } else {
                    result.add(serializeDynamic(item));
                }
            } else {
                result.add(serializeDynamic(item));
            }
        }

        return result;
    }

    /**
     * Deserializes a list.
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> deserializeList(List<?> data, Class<T> elementType) {
        List<T> result = new ArrayList<>();

        ConfigValueAdapter<?> adapter = ConfigAdapters.get(elementType);

        for (Object item : data) {
            if (item == null) {
                result.add(null);
            } else if (adapter != null) {
                ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                result.add((T) typed.deserialize(item));
            } else if (isPrimitiveOrWrapper(elementType) || elementType == String.class) {
                result.add((T) convertValue(item, elementType));
            } else if (elementType.isAnnotationPresent(ConfigSerializable.class) && item instanceof Map) {
                // Recursive deserialization - security check is already in deserialize method
                result.add(deserialize((Map<String, Object>) item, elementType));
            } else {
                result.add((T) item);
            }
        }

        return result;
    }

    /**
     * Serializes a map.
     */
    private static Map<String, Object> serializeMap(Map<?, ?> map, Class<?> valueType) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (value == null) {
                result.put(key, null);
            } else if (valueType != null) {
                ConfigValueAdapter<?> adapter = ConfigAdapters.get(valueType);
                if (adapter != null) {
                    @SuppressWarnings("unchecked")
                    ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                    result.put(key, typed.serialize(value));
                } else if (valueType.isAnnotationPresent(ConfigSerializable.class) && !(value instanceof Map)) {
                    result.put(key, serialize(value));
                } else {
                    result.put(key, serializeDynamic(value));
                }
            } else {
                result.put(key, serializeDynamic(value));
            }
        }

        return result;
    }

    private static Object serializeDynamic(Object value) {
        if (value == null) return null;
        if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
            return value;
        }
        if (value instanceof List) {
            return serializeList((List<?>) value, null);
        }
        if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value, null);
        }
        if (value.getClass().isAnnotationPresent(ConfigSerializable.class)) {
            return serialize(value);
        }
        ConfigValueAdapter<?> adapter = ConfigAdapters.get(value.getClass());
        if (adapter != null) {
            @SuppressWarnings("unchecked")
            ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
            return typed.serialize(value);
        }
        return value.toString();
    }

    private static Class<?> extractListElementType(Field field) {
        if (field == null) return null;
        if (!(field.getGenericType() instanceof ParameterizedType parameterizedType)) return null;
        Type arg = parameterizedType.getActualTypeArguments()[0];
        if (arg instanceof Class<?> cls) {
            return cls;
        }
        if (arg instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        return null;
    }

    private static Class<?> extractMapValueType(Field field) {
        if (field == null) return null;
        if (!(field.getGenericType() instanceof ParameterizedType parameterizedType)) return null;
        Type arg = parameterizedType.getActualTypeArguments()[1];
        if (arg instanceof Class<?> cls) {
            return cls;
        }
        if (arg instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        return null;
    }

    private static String resolveKey(Field field) {
        ConfigValue cv = field.getAnnotation(ConfigValue.class);
        if (cv != null && !cv.value().isEmpty()) {
            return cv.value();
        }
        return field.getName();
    }

    /**
     * Deserializes a map using adapters/serializable types for values.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deserializeMap(Map<?, ?> data, Class<?> valueType) {
        Map<String, Object> result = new LinkedHashMap<>();

        ConfigValueAdapter<?> adapter = ConfigAdapters.get(valueType);

        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object raw = entry.getValue();

            if (raw == null) {
                result.put(key, null);
                continue;
            }

            if (adapter != null) {
                ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                result.put(key, typed.deserialize(raw));
            } else if (valueType.isAnnotationPresent(ConfigSerializable.class) && raw instanceof Map) {
                result.put(key, deserialize((Map<String, Object>) raw, valueType));
            } else {
                result.put(key, raw);
            }
        }

        return result;
    }

    /**
     * Converts value to target type.
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null)
            return null;

        String stringValue = value.toString();

        if (targetType == String.class)
            return stringValue;
        if (targetType == int.class || targetType == Integer.class)
            return Integer.parseInt(stringValue);
        if (targetType == long.class || targetType == Long.class)
            return Long.parseLong(stringValue);
        if (targetType == boolean.class || targetType == Boolean.class)
            return Boolean.parseBoolean(stringValue);
        if (targetType == double.class || targetType == Double.class)
            return Double.parseDouble(stringValue);
        if (targetType == float.class || targetType == Float.class)
            return Float.parseFloat(stringValue);
        if (targetType == byte.class || targetType == Byte.class)
            return Byte.parseByte(stringValue);
        if (targetType == short.class || targetType == Short.class)
            return Short.parseShort(stringValue);

        return value;
    }

    /**
     * Checks if type is primitive or wrapper.
     */
    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Integer.class ||
                type == Long.class ||
                type == Boolean.class ||
                type == Double.class ||
                type == Float.class ||
                type == Byte.class ||
                type == Short.class ||
                type == Character.class;
    }
}
