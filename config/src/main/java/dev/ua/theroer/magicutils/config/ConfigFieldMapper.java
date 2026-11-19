package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Supplier;

/**
 * Maps annotated config classes ({@link ConfigValue}, {@link ConfigSection},
 * {@link SaveTo}, {@link DefaultValue}, ...) to and from {@link ConfigDocument}s:
 * default generation, (partial) loading, saving and external-file fields.
 */
final class ConfigFieldMapper {
    private static final int CLONE_SNAPSHOT_RETRY_LIMIT = 8;

    private final Platform platform;
    private final PlatformLogger logger;

    ConfigFieldMapper(Platform platform, PlatformLogger logger) {
        this.platform = platform;
        this.logger = logger;
    }

    void writeDefaults(Object instance, ConfigDocument document, Class<?> clazz, String prefix) throws Exception {
        for (Field field : getConfigFields(clazz)) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);

            if (field.getAnnotation(SaveTo.class) != null) {
                continue;
            }

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Object sectionInstance = field.get(instance);
                if (sectionInstance == null) {
                    Object defaultValue = getDefaultValue(field);
                    if (defaultValue != null) {
                        sectionInstance = defaultValue;
                    } else {
                        sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                    }
                    field.set(instance, sectionInstance);
                }
                Map<String, Object> sectionData = new LinkedHashMap<>();
                Map<String, List<String>> comments = new LinkedHashMap<>();
                writeSectionToMap(sectionInstance, sectionData, "", comments);
                document.set(path, sectionData, comments.get(path));
                document.addComments(comments, path.isEmpty() ? "" : path + ".");
                continue;
            }

            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                Object defaultValue = field.get(instance);
                if (defaultValue == null) {
                    defaultValue = getDefaultValue(field);
                }
                if (defaultValue == null && field.getType().isPrimitive()) {
                    defaultValue = getPrimitiveDefault(field.getType());
                }

                if (defaultValue != null) {
                    Object valueToSave = prepareForConfig(defaultValue, field);
                    document.set(path, valueToSave, commentLines(field.getAnnotation(Comment.class)));
                    field.set(instance, cloneIfNeeded(defaultValue));
                }
            }
        }
    }

    void processFields(Object instance, ConfigDocument document, Class<?> clazz,
                               String prefix, Set<String> sections) throws Exception {
        for (Field field : getConfigFields(clazz)) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);
            if (!shouldProcessPath(path, sections)) {
                continue;
            }

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Object sectionInstance = field.get(instance);
                if (sectionInstance == null) {
                    sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                    field.set(instance, sectionInstance);
                }
                ConfigSectionView subsection = document.getConfigurationSection(path);
                if (subsection != null) {
                    processFields(sectionInstance, new ConfigDocument(subsection.unwrap(), document.header),
                            field.getType(), "", narrowSections(path, sections));
                }
                continue;
            }

            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                SaveTo saveTo = field.getAnnotation(SaveTo.class);
                ConfigDocument source = document;
                if (saveTo != null) {
                    File file = new ConfigMetadata(saveTo.value(), true, null, platform.configDir())
                            .resolveFile(platform.configDir());
                    source = ConfigDocument.load(file);
                }
                loadFieldValue(instance, field, source, path);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFieldValue(Object instance, Field field, ConfigDocument document, String path) throws Exception {
        if (!document.contains(path)) {
            Object existingValue = field.get(instance);
            if (existingValue != null) {
                return;
            }

            Object defaultValue = getDefaultValue(field);
            if (defaultValue == null && field.getType().isPrimitive()) {
                defaultValue = getPrimitiveDefault(field.getType());
            }

            if (defaultValue != null) {
                field.set(instance, cloneIfNeeded(defaultValue));
            } else if (field.getAnnotation(ConfigValue.class).required()) {
                throw new IllegalStateException("Required config value missing at path: " + path);
            }
            return;
        }

        Object value = document.get(path);

        // If config contains null or explicit null value
        if (value == null) {
            Object fallback = field.get(instance);
            if (fallback == null) {
                fallback = getDefaultValue(field);
            }
            if (fallback == null && field.getType().isPrimitive()) {
                fallback = getPrimitiveDefault(field.getType());
            }
            if (fallback != null) {
                field.set(instance, cloneIfNeeded(fallback));
                return;
            }
            return;
        }

        Class<?> fieldType = field.getType();

        if (List.class.isAssignableFrom(fieldType) && value instanceof List) {
                Class<?> elementType = null;
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType listType) {
                    Type[] typeArgs = listType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        elementType = resolveRawClass(typeArgs[0]);
                    }
                }

                List<?> list = (List<?>) value;
                if (elementType != null && elementType.isAnnotationPresent(ConfigSerializable.class)) {
                    List<Object> deserializedList = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map) {
                            deserializedList.add(ConfigSerializer.deserialize(logger, (Map<String, Object>) item, elementType));
                        } else {
                            deserializedList.add(item);
                        }
                    }
                    value = deserializedList;
                } else {
                    ConfigValueAdapter<?> adapter = elementType != null ? ConfigAdapters.get(elementType) : null;
                    if (adapter != null) {
                        ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                        List<Object> converted = new ArrayList<>(list.size());
                        for (Object item : list) {
                            converted.add(typed.deserialize(item));
                        }
                        value = converted;
                    }
                }

                ListProcessor processor = field.getAnnotation(ListProcessor.class);
                if (processor != null) {
                    value = processList(field, (List<?>) value);
                }
        } else if (Map.class.isAssignableFrom(fieldType) && value instanceof Map) {
                Class<?> keyType = String.class;
                Class<?> valueType = null;
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType mapType) {
                    Type[] typeArgs = mapType.getActualTypeArguments();
                    if (typeArgs.length > 0) {
                        Class<?> resolvedKeyType = resolveRawClass(typeArgs[0]);
                        if (resolvedKeyType != null) {
                            keyType = resolvedKeyType;
                        }
                    }
                    if (typeArgs.length > 1) {
                        valueType = resolveRawClass(typeArgs[1]);
                    }
                }

                Map<Object, Object> map = new LinkedHashMap<>();
                ConfigValueAdapter<?> keyAdapter = keyType == String.class ? null : ConfigAdapters.get(keyType);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    Object rawKey = entry.getKey();
                    Object typedKey = keyAdapter != null ? keyAdapter.deserialize(rawKey) : String.valueOf(rawKey);
                    map.put(typedKey, entry.getValue());
                }
                if (valueType.isAnnotationPresent(ConfigSerializable.class)) {
                    Map<Object, Object> deserializedMap = new HashMap<>();
                    for (Map.Entry<Object, Object> entry : map.entrySet()) {
                        Object entryValue = entry.getValue();
                        if (entryValue instanceof Map) {
                            deserializedMap.put(entry.getKey(),
                                    ConfigSerializer.deserialize(logger, (Map<String, Object>) entryValue, valueType));
                        } else {
                            deserializedMap.put(entry.getKey(), entryValue);
                        }
                    }
                    value = deserializedMap;
                } else if (valueType == String.class) {
                    // flatten works on string-keyed map; ensure keys are stringified
                    Map<String, Object> stringMap = new LinkedHashMap<>();
                    map.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                    value = flattenStringMap(stringMap);
                } else {
                    ConfigValueAdapter<?> adapter = ConfigAdapters.get(valueType);
                    if (adapter != null) {
                        ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                        Map<Object, Object> converted = new LinkedHashMap<>();
                        for (Map.Entry<Object, Object> entry : map.entrySet()) {
                            converted.put(entry.getKey(), typed.deserialize(entry.getValue()));
                        }
                        value = converted;
                    } else {
                        value = map;
                    }
                }
        } else if (fieldType.isAnnotationPresent(ConfigSerializable.class) && value instanceof Map) {
                value = ConfigSerializer.deserialize(logger, (Map<String, Object>) value, fieldType);
        } else {
            ConfigValueAdapter<?> adapter = ConfigAdapters.get(fieldType);
            if (adapter != null) {
                ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                value = typed.deserialize(value);
            }
        }

        if (value == null) {
            Object existing = field.get(instance);
            if (existing != null) {
                value = existing;
            } else if (field.getType().isPrimitive()) {
                value = getPrimitiveDefault(field.getType());
            }
        }

        // Clamp @MinValue/@MaxValue on plain fields too (no-op without bounds).
        value = ConfigSerializer.validateNumericBounds(logger, field, value);

        try {
            field.set(instance, cloneIfNeeded(value));
        } catch (IllegalArgumentException e) {
            Object fallback = getDefaultValue(field);
            if (fallback == null) {
                fallback = field.get(instance);
            }
            if (fallback == null && field.getType().isPrimitive()) {
                fallback = getPrimitiveDefault(field.getType());
            }
            field.set(instance, cloneIfNeeded(fallback));
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> processList(Field field, List<?> list) throws Exception {
        ListProcessor processor = field.getAnnotation(ListProcessor.class);
        if (processor == null)
            return list;

        ListItemProcessor<Object> itemProcessor = (ListItemProcessor<Object>) processor.value().getDeclaredConstructor().newInstance();
        List<Object> result = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            ListItemProcessor.ProcessResult<?> processResult = itemProcessor.process(item, i);

            if (processResult.shouldUseDefault()) {
                DefaultValue defaultAnnotation = field.getAnnotation(DefaultValue.class);
                if (defaultAnnotation != null && defaultAnnotation.provider() != NoDefaultValueProvider.class) {
                    DefaultValueProvider<?> provider = defaultAnnotation.provider().getDeclaredConstructor().newInstance();
                    List<?> defaults = (List<?>) provider.provide();
                    if (i < defaults.size()) {
                        result.add(defaults.get(i));
                        continue;
                    }
                }
            } else {
                result.add(processResult.getValue());
            }
        }
        return result;
    }

    private Object prepareForConfig(Object value, Field field) throws Exception {
        if (value == null) {
            return null;
        }

        if (value instanceof List && List.class.isAssignableFrom(field.getType())) {
            Class<?> elementType = null;
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType listType) {
                Type[] typeArgs = listType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    elementType = resolveRawClass(typeArgs[0]);
                }
            }
            if (elementType != null && elementType.isAnnotationPresent(ConfigSerializable.class)) {
                List<Object> serializedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item == null) {
                        serializedList.add(null);
                    } else if (elementType.isInstance(item)) {
                        serializedList.add(ConfigSerializer.serialize(item));
                    } else {
                        serializedList.add(item);
                    }
                }
                return serializedList;
            }

            ConfigValueAdapter<?> adapter = elementType != null ? ConfigAdapters.get(elementType) : null;
            if (adapter != null) {
                @SuppressWarnings("unchecked")
                ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                List<Object> converted = new ArrayList<>(((List<?>) value).size());
                for (Object item : (List<?>) value) {
                    converted.add(item != null ? typed.serialize(item) : null);
                }
                return converted;
            }
        }

        if (value instanceof Map && Map.class.isAssignableFrom(field.getType())) {
            Class<?> valueType = null;
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType mapType) {
                Type[] typeArgs = mapType.getActualTypeArguments();
                if (typeArgs.length > 1) {
                    valueType = resolveRawClass(typeArgs[1]);
                }
            }
            if (valueType != null && valueType.isAnnotationPresent(ConfigSerializable.class)) {
                Map<String, Object> serializedMap = new LinkedHashMap<>();
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object entryValue = entry.getValue();
                    if (entryValue == null) {
                        serializedMap.put(String.valueOf(entry.getKey()), null);
                    } else if (valueType.isInstance(entryValue)) {
                        serializedMap.put(String.valueOf(entry.getKey()),
                                ConfigSerializer.serialize(entryValue));
                    } else {
                        serializedMap.put(String.valueOf(entry.getKey()), entryValue);
                    }
                }
                return serializedMap;
            }

            if (valueType == String.class) {
                Map<?, ?> map = (Map<?, ?>) value;
                if (hasDottedKeys(map)) {
                    return expandDottedMap(map);
                }
            }

            ConfigValueAdapter<?> adapter = valueType != null ? ConfigAdapters.get(valueType) : null;
            if (adapter != null) {
                @SuppressWarnings("unchecked")
                ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
                Map<String, Object> converted = new LinkedHashMap<>();
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object entryValue = entry.getValue();
                    converted.put(String.valueOf(entry.getKey()),
                            entryValue != null ? typed.serialize(entryValue) : null);
                }
                return converted;
            }
        }

        if (value.getClass().isAnnotationPresent(ConfigSerializable.class)) {
            return ConfigSerializer.serialize(value);
        }

        ConfigValueAdapter<?> adapter = ConfigAdapters.get(value.getClass());
        if (adapter == null && field != null) {
            adapter = ConfigAdapters.get(field.getType());
        }
        if (adapter != null) {
            @SuppressWarnings("unchecked")
            ConfigValueAdapter<Object> typed = (ConfigValueAdapter<Object>) adapter;
            return typed.serialize(value);
        }

        return value;
    }

    private void writeSectionToMap(Object instance, Map<String, Object> out, String prefix, Map<String, List<String>> comments) throws Exception {
        for (Field field : getConfigFields(instance.getClass())) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Object sectionInstance = field.get(instance);
                if (sectionInstance == null) {
                    Object defaultValue = getDefaultValue(field);
                    if (defaultValue != null) {
                        sectionInstance = defaultValue;
                    } else {
                        sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                    }
                    field.set(instance, sectionInstance);
                }
                Map<String, Object> nested = new LinkedHashMap<>();
                writeSectionToMap(sectionInstance, nested, "", comments);
                comments.put(path, commentLines(field.getAnnotation(Comment.class)));
                applyPath(out, path, nested);
                continue;
            }

            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                Object value = field.get(instance);
                if (value == null) {
                    value = getDefaultValue(field);
                }
                if (value == null && field.getType().isPrimitive()) {
                    value = getPrimitiveDefault(field.getType());
                }
                if (value != null) {
                    Object prepared = prepareForConfig(value, field);
                    applyPath(out, path, prepared);
                    comments.put(path, commentLines(field.getAnnotation(Comment.class)));
                }
            }
        }
    }

    private void applyPath(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object child = current.get(part);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                current.put(part, child);
            }
            current = ConfigDocument.castMap((Map<?, ?>) child);
        }
        current.put(parts[parts.length - 1], value);
    }

    private List<String> commentLines(Comment comment) {
        if (comment == null || comment.value().isEmpty()) return null;
        return Arrays.asList(comment.value().split("\n"));
    }

    private boolean hasDottedKeys(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (key != null && key.toString().contains(".")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> expandDottedMap(Map<?, ?> map) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toString() : "null";
            insertDotted(root, key, entry.getValue());
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private void insertDotted(Map<String, Object> node, String key, Object value) {
        int dot = key.indexOf('.');
        if (dot < 0) {
            node.put(key, value);
            return;
        }

        String head = key.substring(0, dot);
        String tail = key.substring(dot + 1);

        Object child = node.get(head);
        if (!(child instanceof Map)) {
            child = new LinkedHashMap<String, Object>();
            node.put(head, child);
        }

        insertDotted((Map<String, Object>) child, tail, value);
    }

    @SuppressWarnings("unchecked")
    private Object cloneIfNeeded(Object value) {
        if (value instanceof List<?>) {
            List<?> snapshot = snapshotWithRetry(() -> new ArrayList<>((List<?>) value));
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<Object>) snapshot) {
                copy.add(cloneIfNeeded(item));
            }
            return copy;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> snapshot = snapshotWithRetry(() -> new LinkedHashMap<>((Map<?, ?>) value));
            Map<Object, Object> copy = new LinkedHashMap<>();
            snapshot.forEach((key, val) -> copy.put(key, cloneIfNeeded(val)));
            return copy;
        }
        return value;
    }

    private <T> T snapshotWithRetry(Supplier<T> supplier) {
        ConcurrentModificationException lastFailure = null;
        for (int attempt = 0; attempt < CLONE_SNAPSHOT_RETRY_LIMIT; attempt++) {
            try {
                return supplier.get();
            } catch (ConcurrentModificationException failure) {
                lastFailure = failure;
                Thread.yield();
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("Failed to snapshot mutable config value");
    }

    private Object getPrimitiveDefault(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == float.class)
            return 0F;
        if (type == double.class)
            return 0D;
        if (type == char.class)
            return '\0';
        return null;
    }

    private Object getDefaultValue(Field field) throws Exception {
        DefaultValue defaultAnnotation = field.getAnnotation(DefaultValue.class);
        if (defaultAnnotation == null)
            return null;

        if (defaultAnnotation.provider() != NoDefaultValueProvider.class) {
            DefaultValueProvider<?> provider = defaultAnnotation.provider().getDeclaredConstructor().newInstance();
            return provider.provide();
        }

        String stringValue = defaultAnnotation.value();
        if (stringValue.isEmpty())
            return null;

        return parseValue(stringValue, field.getType());
    }

    private Map<String, String> flattenStringMap(Map<String, Object> map) {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", map, flat);
        return flat;
    }

    private void flatten(String prefix, Map<String, Object> source, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, ConfigDocument.castMap(nested), out);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
    }

    private Object parseValue(String value, Class<?> type) {
        if (type == String.class)
            return value;
        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);
        if (type == long.class || type == Long.class)
            return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);
        if (type == float.class || type == Float.class)
            return Float.parseFloat(value);
        return value;
    }

    private boolean isConfigField(Field field) {
        return field.isAnnotationPresent(ConfigValue.class) ||
                field.isAnnotationPresent(ConfigSection.class);
    }

    private String getFieldPath(Field field, String prefix) {
        ConfigValue configValue = field.getAnnotation(ConfigValue.class);
        if (configValue != null && !configValue.value().isEmpty()) {
            return prefix + configValue.value();
        }

        ConfigSection configSection = field.getAnnotation(ConfigSection.class);
        if (configSection != null && !configSection.value().isEmpty()) {
            return prefix + configSection.value();
        }

        return prefix + field.getName();
    }

    private List<Field> getConfigFields(Class<?> clazz) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(current);
        }
        Collections.reverse(hierarchy);

        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    Set<String> normalizeSections(Set<String> sections) {
        if (sections == null || sections.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String section : sections) {
            if (section == null) {
                continue;
            }
            String trimmed = section.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized.isEmpty() ? Collections.emptySet() : normalized;
    }

    private boolean shouldProcessPath(String path, Set<String> sections) {
        if (sections == null || sections.isEmpty()) {
            return true;
        }
        for (String section : sections) {
            if (section.equals(path)
                    || section.startsWith(path + ".")
                    || path.startsWith(section + ".")) {
                return true;
            }
        }
        return false;
    }

    private Set<String> narrowSections(String parentPath, Set<String> sections) {
        if (sections == null || sections.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> nested = new LinkedHashSet<>();
        for (String section : sections) {
            if (section.equals(parentPath)) {
                return Collections.emptySet();
            }
            if (section.startsWith(parentPath + ".")) {
                nested.add(section.substring(parentPath.length() + 1));
            }
        }
        return nested.isEmpty() ? Collections.emptySet() : nested;
    }

    private Class<?> resolveRawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawClass) {
            return rawClass;
        }
        return null;
    }

    void saveFields(Object instance, ConfigDocument document, Class<?> clazz, String prefix) throws Exception {
        for (Field field : getConfigFields(clazz)) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);
            Object value = field.get(instance);
            if (value == null) {
                value = getDefaultValue(field);
            }
            if (value == null && field.getType().isPrimitive()) {
                value = getPrimitiveDefault(field.getType());
            }
            if (value == null) {
                continue;
            }
            value = cloneIfNeeded(value);
            field.set(instance, value);

            if (field.getAnnotation(SaveTo.class) != null) {
                continue;
            }

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Map<String, Object> sectionData = new LinkedHashMap<>();
                Map<String, List<String>> comments = new LinkedHashMap<>();
                writeSectionToMap(value, sectionData, "", comments);
                document.set(path, sectionData, comments.get(path));
                document.addComments(comments, path.isEmpty() ? "" : path + ".");
                continue;
            }

            Object valueToSave = prepareForConfig(value, field);

            document.set(path, valueToSave, commentLines(field.getAnnotation(Comment.class)));
        }
    }

    /**
     * Writes every {@link SaveTo} field to its own file. Fields sharing a
     * {@code @SaveTo} path go into one document, keyed by the field's config path.
     */
    void flushExternalFields(Object instance, Class<?> clazz, String prefix) throws Exception {
        Map<String, ConfigDocument> externalDocs = new LinkedHashMap<>();
        collectExternalFields(instance, clazz, prefix, externalDocs);
        for (Map.Entry<String, ConfigDocument> entry : externalDocs.entrySet()) {
            File file = new ConfigMetadata(entry.getKey(), true, null, platform.configDir())
                    .resolveFile(platform.configDir());
            ConfigDocument.ensureParentDirectory(file);
            entry.getValue().save(file);
        }
    }

    private void collectExternalFields(Object instance, Class<?> clazz, String prefix,
                                       Map<String, ConfigDocument> externalDocs) throws Exception {
        for (Field field : getConfigFields(clazz)) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);

            SaveTo saveTo = field.getAnnotation(SaveTo.class);
            if (saveTo != null) {
                Object value = field.get(instance);
                if (value == null) {
                    value = getDefaultValue(field);
                }
                if (value == null && field.getType().isPrimitive()) {
                    value = getPrimitiveDefault(field.getType());
                }
                if (value == null) {
                    continue;
                }
                ConfigDocument document =
                        externalDocs.computeIfAbsent(saveTo.value(), k -> ConfigDocument.empty());
                document.set(path, prepareForConfig(value, field),
                        commentLines(field.getAnnotation(Comment.class)));
                continue;
            }

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Object sectionInstance = field.get(instance);
                if (sectionInstance != null) {
                    collectExternalFields(sectionInstance, field.getType(), "", externalDocs);
                }
            }
        }
    }
}
