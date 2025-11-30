package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import lombok.Getter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;

import org.yaml.snakeyaml.Yaml;

/**
 * Platform-agnostic configuration manager using annotation-driven mapping and SnakeYAML.
 */
public class ConfigManager {
    private final Platform platform;
    private final PlatformLogger logger;
    private final Map<ConfigKey, ConfigEntry<?>> configs = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<ConfigKey>> classIndex = new ConcurrentHashMap<>();
    private final Map<Object, ConfigKey> instanceIndex = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> primaryInstances = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<BiConsumer<Object, Set<String>>>> changeListeners = new ConcurrentHashMap<>();

    private final Map<Path, Set<ConfigKey>> fileIndex = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> directoryWatchKeys = new ConcurrentHashMap<>();
    private final Map<Path, Integer> directoryRefCounts = new ConcurrentHashMap<>();
    private WatchService watchService;
    private ExecutorService watcherExecutor;
    private volatile boolean shuttingDown = false;

    /**
     * Creates a new ConfigManager for the provided platform.
     *
     * @param platform platform abstraction
     */
    public ConfigManager(Platform platform) {
        this.platform = platform;
        this.logger = platform.logger();
    }

    /**
     * Registers and loads a configuration class.
     *
     * @param configClass the configuration class
     * @param <T>         the type of configuration
     * @return the loaded configuration instance
     */
    public <T> T register(Class<T> configClass) {
        return register(configClass, new HashMap<>());
    }

    /**
     * Registers and loads a configuration class with placeholder replacements.
     *
     * @param configClass  the configuration class
     * @param placeholders placeholder replacements for the file path
     * @param <T>          the type of configuration
     * @return the loaded configuration instance
     */
    public <T> T register(Class<T> configClass, Map<String, String> placeholders) {
        try {
            ConfigFile configFile = configClass.getAnnotation(ConfigFile.class);
            if (configFile == null) {
                throw new IllegalArgumentException("Missing @ConfigFile on " + configClass.getName());
            }

            Map<String, String> placeholdersCopy = placeholders == null ? new HashMap<>() : new HashMap<>(placeholders);

            ConfigMetadata metadata = new ConfigMetadata(configFile, placeholdersCopy, platform.configDir());
            ConfigKey key = new ConfigKey(configClass, metadata.getFilePath());

            @SuppressWarnings("unchecked")
            ConfigEntry<T> existingEntry = (ConfigEntry<T>) configs.get(key);
            if (existingEntry != null) {
                return existingEntry.instance;
            }

            T instance = configClass.getDeclaredConstructor().newInstance();
            loadConfig(instance, metadata);

            ConfigEntry<T> entry = new ConfigEntry<>(key, instance, metadata,
                    platform.configDir().resolve(metadata.getFilePath()).toFile());
            entry.refreshLastModified();

            configs.put(key, entry);
            instanceIndex.put(instance, key);
            classIndex.computeIfAbsent(configClass, clazz -> ConcurrentHashMap.newKeySet()).add(key);
            primaryInstances.putIfAbsent(configClass, instance);

            registerWatcher(entry);
            return instance;

        } catch (Exception e) {
            logger.error("Failed to register config: " + configClass.getName(), e);
            throw new RuntimeException("Failed to register config", e);
        }
    }

    private <T> void loadConfig(T instance, ConfigMetadata metadata) throws Exception {
        File configFile = metadata.resolveFile(platform.configDir());

        if (!configFile.exists() && metadata.isAutoCreate()) {
            createDefaultConfig(instance, configFile, metadata);
        }

        YamlDocument yaml = YamlDocument.load(configFile);
        processFields(instance, yaml, instance.getClass());
    }

    private <T> void createDefaultConfig(T instance, File configFile, ConfigMetadata metadata) throws Exception {
        configFile.getParentFile().mkdirs();

        YamlDocument yaml = new YamlDocument();

        Comment classComment = instance.getClass().getAnnotation(Comment.class);
        if (classComment != null) {
            yaml.setHeader(Arrays.asList(classComment.value().split("\n")));
        }

        writeDefaults(instance, yaml, instance.getClass(), "");
        yaml.save(configFile);
        logger.info("Created default config: " + configFile.getName());
    }

    private void writeDefaults(Object instance, YamlDocument yaml, Class<?> clazz, String prefix) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
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
                Map<String, Object> sectionData = new LinkedHashMap<>();
                Map<String, List<String>> comments = new LinkedHashMap<>();
                writeSectionToMap(sectionInstance, sectionData, "", comments);
                yaml.set(path, sectionData, comments.get(path));
                yaml.addComments(comments, path.isEmpty() ? "" : path + ".");
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
                    Object valueToSave = prepareForYaml(defaultValue, field);
                    yaml.set(path, valueToSave, commentLines(field.getAnnotation(Comment.class)));
                    field.set(instance, cloneIfNeeded(defaultValue));
                }
            }
        }
    }

    private void processFields(Object instance, YamlDocument yaml, Class<?> clazz) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, "");

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Object sectionInstance = field.get(instance);
                if (sectionInstance == null) {
                    sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                    field.set(instance, sectionInstance);
                }
                YamlSection subsection = yaml.getConfigurationSection(path);
                if (subsection != null) {
                    processFields(sectionInstance, new YamlDocument(subsection.unwrap(), yaml.header), field.getType());
                }
                continue;
            }

            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                loadFieldValue(instance, field, yaml, path);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFieldValue(Object instance, Field field, YamlDocument yaml, String path) throws Exception {
        if (!yaml.contains(path)) {
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

        Object value = yaml.get(path);

        // If YAML contains null or explicit null value
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

        if (value != null) {
            Class<?> fieldType = field.getType();

            if (List.class.isAssignableFrom(fieldType) && value instanceof List) {
                ParameterizedType listType = (ParameterizedType) field.getGenericType();
                Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];

                List<?> list = (List<?>) value;
                if (elementType.isAnnotationPresent(ConfigSerializable.class)) {
                    List<Object> deserializedList = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map) {
                            deserializedList.add(ConfigSerializer.deserialize((Map<String, Object>) item, elementType));
                        } else {
                            deserializedList.add(item);
                        }
                    }
                    value = deserializedList;
                }

                ListProcessor processor = field.getAnnotation(ListProcessor.class);
                if (processor != null) {
                    value = processList(field, (List<?>) value);
                }
            } else if (Map.class.isAssignableFrom(fieldType) && value instanceof Map) {
                ParameterizedType mapType = (ParameterizedType) field.getGenericType();
                Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];

                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                if (valueType.isAnnotationPresent(ConfigSerializable.class)) {
                    Map<String, Object> deserializedMap = new HashMap<>();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        Object entryValue = entry.getValue();
                        if (entryValue instanceof Map) {
                            deserializedMap.put(entry.getKey(),
                                    ConfigSerializer.deserialize((Map<String, Object>) entryValue, valueType));
                        } else {
                            deserializedMap.put(entry.getKey(), entryValue);
                        }
                    }
                    value = deserializedMap;
                } else {
                    value = map;
                }
            } else if (fieldType.isAnnotationPresent(ConfigSerializable.class) && value instanceof Map) {
                value = ConfigSerializer.deserialize((Map<String, Object>) value, fieldType);
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

    private Object prepareForYaml(Object value, Field field) throws Exception {
        if (value == null) {
            return null;
        }

        if (value instanceof List && List.class.isAssignableFrom(field.getType())) {
            ParameterizedType listType = (ParameterizedType) field.getGenericType();
            Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];
            if (elementType.isAnnotationPresent(ConfigSerializable.class)) {
                List<Map<String, Object>> serializedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    serializedList.add(item != null ? ConfigSerializer.serialize(item) : null);
                }
                return serializedList;
            }
        }

        if (value instanceof Map && Map.class.isAssignableFrom(field.getType())) {
            ParameterizedType mapType = (ParameterizedType) field.getGenericType();
            Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];
            if (valueType.isAnnotationPresent(ConfigSerializable.class)) {
                Map<String, Object> serializedMap = new LinkedHashMap<>();
                Map<?, ?> map = (Map<?, ?>) value;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object entryValue = entry.getValue();
                    serializedMap.put(String.valueOf(entry.getKey()),
                            entryValue != null ? ConfigSerializer.serialize(entryValue) : null);
                }
                return serializedMap;
            }

            if (valueType == String.class) {
                Map<?, ?> map = (Map<?, ?>) value;
                if (hasDottedKeys(map)) {
                    return expandDottedMap(map);
                }
            }
        }

        if (value.getClass().isAnnotationPresent(ConfigSerializable.class)) {
            return ConfigSerializer.serialize(value);
        }

        return value;
    }

    private void writeSectionToMap(Object instance, Map<String, Object> out, String prefix, Map<String, List<String>> comments) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
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
                    Object prepared = prepareForYaml(value, field);
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
            current = YamlDocument.castMap((Map<?, ?>) child);
        }
        current.put(parts[parts.length - 1], value);
    }

    private Map<String, Object> flattenSection(YamlSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        flattenSectionRecursive("", section, result);
        return result;
    }

    private List<String> commentLines(Comment comment) {
        if (comment == null || comment.value().isEmpty()) return null;
        return Arrays.asList(comment.value().split("\n"));
    }

    private void flattenSectionRecursive(String prefix, YamlSection section, Map<String, Object> output) {
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (raw instanceof Map) {
                flattenSectionRecursive(fullKey, new YamlSection(YamlDocument.castMap((Map<?, ?>) raw)), output);
            } else {
                output.put(fullKey, raw);
            }
        }
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
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                copy.add(cloneIfNeeded(item));
            }
            return copy;
        }
        if (value instanceof Map<?, ?>) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, val) -> copy.put(key, cloneIfNeeded(val)));
            return copy;
        }
        return value;
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

    /**
     * Saves all registered instances of the given config class.
     *
     * @param <T> config type
     * @param configClass config type to save
     */
    public <T> void save(Class<T> configClass) {
        for (ConfigEntry<?> entry : getEntries(configClass)) {
            saveEntry(entry);
        }
    }

    /**
     * Saves the provided configuration instance.
     *
     * @param configInstance config instance to persist
     */
    public void save(Object configInstance) {
        ConfigEntry<?> entry = getEntry(configInstance);
        if (entry != null) {
            saveEntry(entry);
        }
    }

    /**
     * Reloads the provided configuration instance from disk.
     *
     * @param configInstance config instance to reload
     */
    public void reload(Object configInstance) {
        ConfigEntry<?> entry = getEntry(configInstance);
        if (entry == null) {
            return;
        }

        if (reloadEntry(entry, Collections.emptySet(), false)) {
            ConfigReloadable reloadable = entry.key.configClass.getAnnotation(ConfigReloadable.class);
            if (reloadable != null && reloadable.notifyOnChange()) {
                notifyChangeListeners(entry, Collections.emptySet());
            }
        }
    }

    /**
     * Alias for {@link #reload(Object)}.
     *
     * @param configInstance config instance to reload
     */
    public void mergeFromFile(Object configInstance) {
        reload(configInstance);
    }

    /**
     * Alias for {@link #save(Object)}.
     *
     * @param configInstance config instance to save
     */
    public void mergeToFile(Object configInstance) {
        save(configInstance);
    }

    /**
     * Unregisters the configuration and stops tracking file changes.
     *
     * @param configInstance config instance to remove
     */
    public void unload(Object configInstance) {
        ConfigEntry<?> entry = getEntry(configInstance);
        if (entry != null) {
            removeEntry(entry);
        }
    }

    private void saveFields(Object instance, YamlDocument yaml, Class<?> clazz, String prefix) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
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

            SaveTo saveTo = field.getAnnotation(SaveTo.class);
            if (saveTo != null) {
                continue;
            }

            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Map<String, Object> sectionData = new LinkedHashMap<>();
                Map<String, List<String>> comments = new LinkedHashMap<>();
                writeSectionToMap(value, sectionData, "", comments);
                yaml.set(path, sectionData, comments.get(path));
                yaml.addComments(comments, path.isEmpty() ? "" : path + ".");
                continue;
            }

            Object valueToSave = prepareForYaml(value, field);

            yaml.set(path, valueToSave, commentLines(field.getAnnotation(Comment.class)));
        }
    }

    /**
     * Retrieves the primary registered instance of the given config class.
     *
     * @param <T> config type
     * @param configClass config type to fetch
     * @return primary instance or null if none registered
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<T> configClass) {
        return (T) primaryInstances.get(configClass);
    }

    /**
     * Reloads all registered instances of a config class from disk.
     *
     * @param <T> config type
     * @param configClass config type to reload
     */
    public <T> void reload(Class<T> configClass) {
        reload(configClass, new String[0]);
    }

    /**
     * Reloads registered instances of a config class, optionally scoping to specific sections.
     *
     * @param <T> config type
     * @param configClass config type to reload
     * @param sections sections to reload (empty for full reload)
     */
    public <T> void reload(Class<T> configClass, String... sections) {
        Set<String> sectionSet = (sections == null || sections.length == 0)
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(sections));

        ConfigReloadable reloadable = configClass.getAnnotation(ConfigReloadable.class);
        if (reloadable != null && reloadable.sections().length > 0 && !sectionSet.isEmpty()) {
            Set<String> allowedSections = new HashSet<>(Arrays.asList(reloadable.sections()));
            for (String section : sectionSet) {
                if (!allowedSections.contains(section)) {
                    logger.warn("Section '" + section + "' is not reloadable for " + configClass.getSimpleName());
                    return;
                }
            }
        }

        for (ConfigEntry<?> entry : getEntries(configClass)) {
            if (reloadEntry(entry, sectionSet, false) && reloadable != null && reloadable.notifyOnChange()) {
                notifyChangeListeners(entry, sectionSet);
            }
        }
    }

    /**
     * Registers a listener that fires when the config changes on disk.
     *
     * @param <T> config type
     * @param configClass config type to monitor
     * @param listener callback receiving updated instance and changed sections
     */
    @SuppressWarnings("unchecked")
    public <T> void onChange(Class<T> configClass, BiConsumer<T, Set<String>> listener) {
        changeListeners.computeIfAbsent(configClass, k -> new ArrayList<>())
                .add((BiConsumer<Object, Set<String>>) listener);
    }

    private void notifyChangeListeners(ConfigEntry<?> entry, Set<String> changedSections) {
        List<BiConsumer<Object, Set<String>>> listeners = changeListeners.get(entry.key.configClass);
        if (listeners == null) {
            return;
        }

        for (BiConsumer<Object, Set<String>> listener : listeners) {
            listener.accept(entry.instance, changedSections);
        }
    }

    private Collection<ConfigEntry<?>> getEntries(Class<?> configClass) {
        Set<ConfigKey> keys = classIndex.get(configClass);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConfigEntry<?>> result = new ArrayList<>(keys.size());
        for (ConfigKey key : keys) {
            ConfigEntry<?> entry = configs.get(key);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private ConfigEntry<?> getEntry(Object configInstance) {
        if (configInstance == null) {
            return null;
        }

        ConfigKey key = instanceIndex.get(configInstance);
        return key != null ? configs.get(key) : null;
    }

    private void removeEntry(ConfigEntry<?> entry) {
        configs.remove(entry.key);
        instanceIndex.remove(entry.instance);

        Set<ConfigKey> keys = classIndex.get(entry.key.configClass);
        if (keys != null) {
            keys.remove(entry.key);
            if (keys.isEmpty()) {
                classIndex.remove(entry.key.configClass);
                if (primaryInstances.get(entry.key.configClass) == entry.instance) {
                    primaryInstances.remove(entry.key.configClass);
                }
            } else if (primaryInstances.get(entry.key.configClass) == entry.instance) {
                ConfigEntry<?> nextEntry = configs.get(keys.iterator().next());
                if (nextEntry != null) {
                    primaryInstances.put(entry.key.configClass, nextEntry.instance);
                } else {
                    primaryInstances.remove(entry.key.configClass);
                }
            }
        }

        Path filePath = entry.file.toPath().toAbsolutePath();
        Set<ConfigKey> fileKeys = fileIndex.get(filePath);
        if (fileKeys != null) {
            fileKeys.remove(entry.key);
            if (fileKeys.isEmpty()) {
                fileIndex.remove(filePath);
            }
        }

        Path dir = filePath.getParent();
        directoryRefCounts.computeIfPresent(dir, (path, count) -> {
            int next = count - 1;
            if (next <= 0) {
                WatchKey watchKey = directoryWatchKeys.remove(path);
                if (watchKey != null) {
                    watchKey.cancel();
                }
                return null;
            }
            return next;
        });
    }

    private void saveEntry(ConfigEntry<?> entry) {
        synchronized (entry.monitor) {
            try {
                long currentFileModified = entry.file.exists() ? entry.file.lastModified() : -1L;
                long lastKnownModified = entry.getLastModified();

                if (entry.file.exists() && currentFileModified > lastKnownModified) {
                    reloadEntry(entry, Collections.emptySet(), true);
                    entry.refreshLastModified();
                }

                YamlDocument yaml = new YamlDocument();
                saveFields(entry.instance, yaml, entry.instance.getClass(), "");

                entry.file.getParentFile().mkdirs();
                yaml.save(entry.file);
                entry.refreshLastModified();
            } catch (Exception e) {
            logger.error("Failed to save config " + entry.key.configClass.getName() + " (" + entry.metadata.getFilePath()
                    + ")", e);
            }
        }
    }

    private boolean reloadEntry(ConfigEntry<?> entry, Set<String> sections, boolean externalTrigger) {
        synchronized (entry.monitor) {
            try {
                long beforeReload = entry.file.exists() ? entry.file.lastModified() : -1L;
                loadConfig(entry.instance, entry.metadata);

                entry.lastModified.set(beforeReload);
                entry.refreshLastModified();
                return true;
            } catch (Exception e) {
                String reason = externalTrigger ? "external" : "internal";
                logger.error("Failed to reload config " + entry.key.configClass.getName() + " (" + entry.metadata.getFilePath()
                        + ") via " + reason, e);
                return false;
            }
        }
    }

    private void registerWatcher(ConfigEntry<?> entry) {
        File file = entry.file;
        Path filePath = file.toPath().toAbsolutePath();
        Path directory = filePath.getParent();

        fileIndex.computeIfAbsent(filePath, key -> ConcurrentHashMap.newKeySet()).add(entry.key);

        try {
            ensureWatchService();

            if (!directoryWatchKeys.containsKey(directory)) {
                if (!directory.toFile().exists()) {
                    directory.toFile().mkdirs();
                }

                WatchKey watchKey = directory.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                directoryWatchKeys.put(directory, watchKey);
            }

            directoryRefCounts.merge(directory, 1, Integer::sum);
            startWatcherThread();

        } catch (IOException e) {
            logger.error("Failed to register config watcher for " + entry.metadata.getFilePath(), e);
        }
    }

    private synchronized void ensureWatchService() throws IOException {
        if (watchService == null) {
            watchService = FileSystems.getDefault().newWatchService();
        }
    }

    private synchronized void startWatcherThread() {
        if (watcherExecutor != null) {
            return;
        }

        watcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MagicUtils-ConfigWatcher");
            thread.setDaemon(true);
            return thread;
        });

        watcherExecutor.submit(this::watchLoop);
    }

    private void watchLoop() {
        while (!shuttingDown) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path directory = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path changed = directory.resolve((Path) event.context()).toAbsolutePath();
                Set<ConfigKey> affectedKeys = fileIndex.get(changed);
                if (affectedKeys == null || affectedKeys.isEmpty()) {
                    continue;
                }

                for (ConfigKey configKey : affectedKeys) {
                    ConfigEntry<?> entry = configs.get(configKey);
                    if (entry != null) {
                        scheduleExternalReload(entry);
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                directoryWatchKeys.remove(directory);
                directoryRefCounts.remove(directory);
            }
        }
    }

    private void scheduleExternalReload(ConfigEntry<?> entry) {
        if (shuttingDown) {
            return;
        }

        if (!entry.markReloadScheduled()) {
            return;
        }

        platform.runOnMain(() -> {
            try {
                if (reloadEntry(entry, Collections.emptySet(), true)) {
                    notifyChangeListeners(entry, Collections.emptySet());
                }
            } finally {
                entry.reloadComplete();
            }
        });
    }

    /**
     * Stops the watcher service and releases associated resources.
     */
    public void shutdown() {
        shuttingDown = true;

        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
            try {
                watcherExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watcherExecutor = null;
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Failed to close config watch service", e);
            }
            watchService = null;
        }

        directoryWatchKeys.clear();
        directoryRefCounts.clear();
        fileIndex.clear();
    }

    private static final class ConfigKey {
        private final Class<?> configClass;
        private final String filePath;

        private ConfigKey(Class<?> configClass, String filePath) {
            this.configClass = configClass;
            this.filePath = filePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ConfigKey configKey = (ConfigKey) o;
            return Objects.equals(configClass, configKey.configClass)
                    && Objects.equals(filePath, configKey.filePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configClass, filePath);
        }
    }

    private static final class ConfigEntry<T> {
        private final ConfigKey key;
        private final T instance;
        private final ConfigMetadata metadata;
        private final File file;
        private final Object monitor = new Object();
        private final AtomicBoolean reloadScheduled = new AtomicBoolean(false);
        private final AtomicLong lastModified = new AtomicLong(-1L);

        private ConfigEntry(ConfigKey key, T instance, ConfigMetadata metadata, File file) {
            this.key = key;
            this.instance = instance;
            this.metadata = metadata;
            this.file = file;
        }

        private boolean markReloadScheduled() {
            return reloadScheduled.compareAndSet(false, true);
        }

        private void reloadComplete() {
            reloadScheduled.set(false);
        }

        private void refreshLastModified() {
            long modified = file.exists() ? file.lastModified() : -1L;
            this.lastModified.set(modified);
        }

        private long getLastModified() {
            return this.lastModified.get();
        }
    }

    private static class ConfigMetadata {
        @Getter
        private final String filePath;
        @Getter
        private final boolean autoCreate;
        @Getter
        private final String templatePath;

        ConfigMetadata(ConfigFile annotation, Map<String, String> placeholders, Path baseDir) {
            String path = annotation.value();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            this.filePath = sanitizePath(path, baseDir);
            this.autoCreate = annotation.autoCreate();
            this.templatePath = annotation.template();
        }

        private String sanitizePath(String path, Path baseDir) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Path cannot be null or empty");
            }
            try {
                Path normalizedPath = Paths.get(path).normalize();
                Path fullPath = baseDir.resolve(normalizedPath).normalize();

                if (!fullPath.startsWith(baseDir.normalize())) {
                    throw new SecurityException(
                            "Path traversal detected! Path '" + path +
                                    "' attempts to access files outside config directory.");
                }
                return baseDir.normalize().relativize(fullPath).toString().replace('\\', '/');
            } catch (Exception e) {
                if (e instanceof SecurityException) {
                    throw e;
                }
                throw new IllegalArgumentException("Invalid path: " + path, e);
            }
        }

        private File resolveFile(Path baseDir) {
            return baseDir.resolve(filePath).toFile();
        }
    }

    /**
     * Minimal YAML document helper backed by SnakeYAML.
     */
    private static class YamlDocument extends YamlSection {
        private final Map<String, Object> data;
        private final Map<String, List<String>> comments;
        private List<String> header;

        YamlDocument() {
            this(new LinkedHashMap<>(), null);
        }

        YamlDocument(Map<String, Object> backing, List<String> header) {
            super(backing);
            this.data = backing;
            this.header = header;
            this.comments = new LinkedHashMap<>();
        }

        void addComments(Map<String, List<String>> map, String prefix) {
            if (map == null || map.isEmpty()) return;
            for (Map.Entry<String, List<String>> e : map.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) continue;
                String key = e.getKey();
                String full = prefix.isEmpty() ? key : prefix + key;
                comments.put(full, e.getValue());
            }
        }

        static YamlDocument load(File file) throws IOException {
            if (file == null || !file.exists()) {
                return new YamlDocument();
            }
            try (FileInputStream in = new FileInputStream(file)) {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(in);
                Map<String, Object> map = loaded instanceof Map ? castMap((Map<?, ?>) loaded) : new LinkedHashMap<>();
                return new YamlDocument(map, null);
            }
        }

        void setHeader(List<String> lines) {
            this.header = lines;
        }

        void set(String path, Object value) {
            set(path, value, null);
        }

        void set(String path, Object value, List<String> comment) {
            applyPath(data, path, value);
            if (comment != null && !comment.isEmpty()) {
                comments.put(path, comment);
            }
        }

        boolean contains(String path) {
            return navigate(data, path).found;
        }

        boolean isConfigurationSection(String path) {
            NavigateResult res = navigate(data, path);
            return res.found && res.value instanceof Map;
        }

        YamlSection getConfigurationSection(String path) {
            NavigateResult res = navigate(data, path);
            if (res.found && res.value instanceof Map) {
                return new YamlSection(castMap((Map<?, ?>) res.value));
            }
            return null;
        }

        Object get(String path) {
            NavigateResult res = navigate(data, path);
            return res.found ? res.value : null;
        }

        void save(File file) throws IOException {
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                if (header != null && !header.isEmpty()) {
                    for (String line : header) {
                        writer.write("# " + line + System.lineSeparator());
                    }
                }
                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                options.setIndent(2);
                options.setIndicatorIndent(1);
                options.setDefaultScalarStyle(ScalarStyle.PLAIN);
                Yaml yaml = new Yaml(options);
                writeWithComments(writer, yaml, data, comments, "", 0);
            }
        }

        private static void applyPath(Map<String, Object> root, String path, Object value) {
            String[] parts = path.split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                Object child = current.get(part);
                if (!(child instanceof Map)) {
                    child = new LinkedHashMap<String, Object>();
                    current.put(part, child);
                }
                current = castMap((Map<?, ?>) child);
            }
            current.put(parts[parts.length - 1], value);
        }

        private static NavigateResult navigate(Map<String, Object> root, String path) {
            String[] parts = path.split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length; i++) {
                Object value = current.get(parts[i]);
                if (i == parts.length - 1) {
                    return new NavigateResult(true, value);
                }
                if (!(value instanceof Map)) {
                    return new NavigateResult(false, null);
                }
                current = castMap((Map<?, ?>) value);
            }
            return new NavigateResult(false, null);
        }

        static Map<String, Object> castMap(Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }

        private record NavigateResult(boolean found, Object value) {
        }

        @SuppressWarnings("unchecked")
        private static void writeWithComments(FileWriter writer, Yaml yaml,
                                              Map<String, Object> map,
                                              Map<String, List<String>> comments,
                                              String pathPrefix,
                                              int indentLevel) throws IOException {
            String indent = "  ".repeat(indentLevel);
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
                List<String> commentLines = comments != null ? comments.get(currentPath) : null;
                if (commentLines != null) {
                    for (String line : commentLines) {
                        writer.write(indent + "# " + line + System.lineSeparator());
                    }
                }
                Object value = entry.getValue();
                if (value instanceof Map) {
                    writer.write(indent + key + ":" + System.lineSeparator());
                    writeWithComments(writer, yaml, (Map<String, Object>) value, comments, currentPath, indentLevel + 1);
                } else if (value instanceof List) {
                    writer.write(indent + key + ":" + System.lineSeparator());
                    writeList(writer, yaml, (List<?>) value, comments, currentPath, indentLevel + 1);
                } else if (value instanceof String str && str.contains("\n")) {
                    writer.write(indent + key + ": |" + System.lineSeparator());
                    writeMultilineValue(writer, str, indentLevel + 1);
                } else {
                    String dumped = yaml.dump(value).trim();
                    writer.write(indent + key + ": " + dumped + System.lineSeparator());
                }
            }
        }

        private static void writeList(FileWriter writer, Yaml yaml, List<?> list,
                                      Map<String, List<String>> comments, String pathPrefix, int indentLevel) throws IOException {
            String indent = "  ".repeat(indentLevel);
            for (int i = 0; i < list.size(); i++) {
                Object elem = list.get(i);
                String currentPath = pathPrefix + "[" + i + "]";
                List<String> commentLines = comments != null ? comments.get(currentPath) : null;
                if (commentLines != null) {
                    for (String line : commentLines) {
                        writer.write(indent + "# " + line + System.lineSeparator());
                    }
                }
                if (elem instanceof Map) {
                    writer.write(indent + "- " + System.lineSeparator());
                    writeWithComments(writer, yaml, YamlDocument.castMap((Map<?, ?>) elem), comments, currentPath, indentLevel + 1);
                } else if (elem instanceof List) {
                    writer.write(indent + "- " + System.lineSeparator());
                    writeList(writer, yaml, (List<?>) elem, comments, currentPath, indentLevel + 1);
                } else if (elem instanceof String str && str.contains("\n")) {
                    writer.write(indent + "- |" + System.lineSeparator());
                    writeMultilineValue(writer, str, indentLevel + 2);
                } else {
                    String dumped = yaml.dump(elem).trim();
                    writer.write(indent + "- " + dumped + System.lineSeparator());
                }
            }
        }

        private static void writeMultilineValue(FileWriter writer, String value, int indentLevel) throws IOException {
            String indent = "  ".repeat(indentLevel);
            String[] lines = value.split("\\r?\\n", -1);
            for (String line : lines) {
                writer.write(indent + line + System.lineSeparator());
            }
        }
    }

    /**
     * Lightweight view over a YAML section (backed by a map).
     */
    private static class YamlSection {
        private final Map<String, Object> backing;
        private final List<String> header;

        YamlSection(Map<String, Object> backing) {
            this(backing, null);
        }

        YamlSection(Map<String, Object> backing, List<String> header) {
            this.backing = backing;
            this.header = header;
        }

        Set<String> getKeys(boolean deep) {
            return backing.keySet();
        }

        Object get(String key) {
            return backing.get(key);
        }

        Map<String, Object> unwrap() {
            return backing;
        }
    }
}
