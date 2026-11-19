package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.Tasks;
import dev.ua.theroer.magicutils.platform.ThreadContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Platform-agnostic configuration manager using annotation-driven mapping and Jackson (YAML/JSON/JSONC/TOML).
 */
public class ConfigManager {
    private final Platform platform;
    private final PlatformLogger logger;
    private final TaskScheduler scheduler;
    private final ConfigFormatResolver formatResolver;
    private final ConfigMigrator migrator;
    private final ShutdownHookRegistrar shutdownRegistrar;
    private final Runnable shutdownHook;
    private final Map<ConfigKey, ConfigEntry<?>> configs = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<ConfigKey>> classIndex = new ConcurrentHashMap<>();
    private final Map<Object, ConfigKey> instanceIndex = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> primaryInstances = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<BiConsumer<Object, Set<String>>>> changeListeners = new ConcurrentHashMap<>();

    private final Map<Path, Set<ConfigKey>> fileIndex = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> directoryWatchKeys = new ConcurrentHashMap<>();
    private final Map<Path, Integer> directoryRefCounts = new ConcurrentHashMap<>();
    private final Object watcherLock = new Object();
    private WatchService watchService;
    private ExecutorService watcherExecutor;
    private ExecutorService reloadExecutor;
    private volatile boolean watchServiceUnavailable = false;
    private volatile boolean watchServiceWarned = false;
    private volatile boolean shuttingDown = false;
    /**
     * Guards the {@link #shuttingDown} flag against external reload tasks.
     *
     * <p>{@link #shutdown()} sets {@code shuttingDown=true} under this lock,
     * and each watcher-driven reload task acquires the lock before reading
     * the flag. This eliminates the race where a queued task observes the
     * flag still {@code false} while shutdown is mid-flight, then proceeds
     * to mutate config state and notify listeners after the manager is
     * supposed to be quiet.</p>
     */
    private final Object shutdownLock = new Object();
    private static final int CLONE_SNAPSHOT_RETRY_LIMIT = 8;

    /**
     * Creates a new ConfigManager for the provided platform.
     *
     * @param platform platform abstraction
     */
    @SuppressWarnings("this-escape")
    public ConfigManager(Platform platform) {
        this.platform = platform;
        this.logger = platform.logger();
        this.scheduler = Tasks.scheduler(platform);
        this.formatResolver = new ConfigFormatResolver(platform, logger);
        this.migrator = new ConfigMigrator(logger);
        ShutdownHookRegistrar registrar = platform instanceof ShutdownHookRegistrar hookRegistrar
                ? hookRegistrar
                : null;
        this.shutdownRegistrar = registrar;
        this.shutdownHook = this::shutdown;
        if (registrar != null) {
            registrar.registerShutdownHook(shutdownHook);
        }
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
            ConfigFormatResolver.FormatDecision formatDecision =
                    formatResolver.resolveFormatDecision(configFile, placeholdersCopy);
            ConfigMetadata metadata = new ConfigMetadata(formatDecision.targetPath(), configFile.autoCreate(),
                    configFile.template(), platform.configDir());
            ConfigKey key = new ConfigKey(configClass, metadata.getFilePath());

            @SuppressWarnings("unchecked")
            ConfigEntry<T> existingEntry = (ConfigEntry<T>) configs.get(key);
            if (existingEntry != null) {
                return existingEntry.instance;
            }

            T instance = configClass.getDeclaredConstructor().newInstance();
            LoadResult loadResult;
            if (formatDecision.sourcePath() != null) {
                ConfigMetadata sourceMetadata = new ConfigMetadata(formatDecision.sourcePath(), configFile.autoCreate(),
                        configFile.template(), platform.configDir());
                loadResult = loadConfig(instance, sourceMetadata);
                saveConfigToFile(instance, metadata, loadResult.schemaVersion());
                logger.info("Migrated config " + sourceMetadata.getFilePath()
                        + " -> " + metadata.getFilePath());
            } else {
                loadResult = loadConfig(instance, metadata);
            }

            ConfigEntry<T> entry = new ConfigEntry<>(key, instance, metadata,
                    platform.configDir().resolve(metadata.getFilePath()).toFile());
            entry.schemaVersion = loadResult.schemaVersion();
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

    /**
     * Registers migrations for a config class.
     *
     * @param configClass config type to associate with migrations
     * @param migrations migration steps
     */
    public void registerMigrations(Class<?> configClass, ConfigMigration... migrations) {
        if (configClass == null || migrations == null) {
            return;
        }
        for (ConfigMigration migration : migrations) {
            migrator.register(configClass, migration);
        }
    }

    /**
     * Registers a single migration for a config class.
     *
     * @param configClass config type to associate with migration
     * @param migration migration step
     */
    public void registerMigration(Class<?> configClass, ConfigMigration migration) {
        if (configClass == null || migration == null) {
            return;
        }
        migrator.register(configClass, migration);
    }

    /**
     * Registers and loads a configuration class using placeholder pairs.
     * Accepts {@code key, value, key, value...} and converts them to a map.
     *
     * @param configClass the configuration class
     * @param placeholders key/value pairs for placeholders
     * @param <T> the type of configuration
     * @return loaded configuration instance
     */
    public <T> T loadConfigFile(Class<T> configClass, String... placeholders) {
        return register(configClass, ConfigFormatResolver.toPlaceholderMap(placeholders));
    }

    /**
     * Saves all registered instances of the given config class.
     *
     * @param <T> config type
     * @param configClass config type to save
     */
    public <T> void saveConfigFile(Class<T> configClass) {
        save(configClass);
    }

    /**
     * Saves a specific configuration instance.
     *
     * @param configInstance instance to persist
     */
    public void saveConfigFile(Object configInstance) {
        save(configInstance);
    }

    /**
     * Reloads a configuration class with optional sections.
     *
     * @param <T> config type
     * @param configClass config type to reload
     * @param sections optional sections
     */
    public <T> void reloadConfigFile(Class<T> configClass, String... sections) {
        reload(configClass, sections);
    }

    /**
     * Reloads a specific configuration instance.
     *
     * @param configInstance instance to reload
     */
    public void reloadConfigFile(Object configInstance) {
        reload(configInstance);
    }

    /**
     * Reload all registered configuration instances from disk.
     */
    public void reloadAll() {
        warnIfMainThread("reloadAll");
        List<ConfigEntry<?>> entries = new ArrayList<>(configs.values());
        for (ConfigEntry<?> entry : entries) {
                if (reloadEntry(entry, Collections.emptySet(), false) && shouldNotifyOnChange(entry)) {
                    notifyChangeListeners(entry, Collections.emptySet());
                }
            }
        }

    /**
     * Unregisters all instances of a config class.
     *
     * @param configClass config type to unload
     */
    public void unloadConfigFile(Class<?> configClass) {
        for (ConfigEntry<?> entry : new ArrayList<>(getEntries(configClass))) {
            removeEntry(entry);
        }
    }

    private <T> LoadResult loadConfig(T instance, ConfigMetadata metadata) throws Exception {
        return loadConfig(instance, metadata, Collections.emptySet());
    }

    /**
     * Loads a config with one retry on ConcurrentModificationException.
     * <p>
     * If a user-supplied Map/List in the config instance throws CME during the first
     * iteration (a transient state, e.g. another thread mutating it), retry once —
     * the second attempt typically succeeds because the new document load replaces
     * the field values before iteration is needed again.
     */
    private <T> LoadResult loadConfigWithRetry(T instance, ConfigMetadata metadata, Set<String> sections) throws Exception {
        try {
            return loadConfig(instance, metadata, sections);
        } catch (java.util.ConcurrentModificationException firstCme) {
            try {
                return loadConfig(instance, metadata, sections);
            } catch (java.util.ConcurrentModificationException secondCme) {
                secondCme.addSuppressed(firstCme);
                throw secondCme;
            }
        }
    }

    private <T> LoadResult loadConfig(T instance, ConfigMetadata metadata, Set<String> sections) throws Exception {
        File configFile = metadata.resolveFile(platform.configDir());
        boolean created = false;

        if (!configFile.exists() && metadata.isAutoCreate()) {
            createDefaultConfig(instance, configFile, metadata);
            created = true;
        }

        ConfigDocument document = ConfigDocument.load(configFile);
        ConfigMigrator.MigrationResult migrationResult = migrator.applyMigrations(instance.getClass(), document, created);
        Set<String> normalizedSections = normalizeSections(sections);
        boolean fullReload = normalizedSections.isEmpty() || migrationResult.shouldSave();
        processFields(instance, document, instance.getClass(), "",
                fullReload ? Collections.emptySet() : normalizedSections);
        if (migrationResult.shouldSave()) {
            saveConfigToFile(instance, metadata, migrationResult.schemaVersion());
        }
        return new LoadResult(migrationResult.schemaVersion(), migrationResult.migrated());
    }

    private <T> void createDefaultConfig(T instance, File configFile, ConfigMetadata metadata) throws Exception {
        ConfigDocument.ensureParentDirectory(configFile);

        ConfigDocument document = ConfigDocument.empty();

        Comment classComment = instance.getClass().getAnnotation(Comment.class);
        if (classComment != null) {
            document.setHeader(Arrays.asList(classComment.value().split("\n")));
        }

        writeDefaults(instance, document, instance.getClass(), "");
        document.save(configFile);
        flushExternalFields(instance, instance.getClass(), "");
        logger.info("Created default config: " + configFile.getName());
    }

    private void writeDefaults(Object instance, ConfigDocument document, Class<?> clazz, String prefix) throws Exception {
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

    private void processFields(Object instance, ConfigDocument document, Class<?> clazz,
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

    private Set<String> normalizeSections(Set<String> sections) {
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
        warnIfMainThread("reload");
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

    private void saveFields(Object instance, ConfigDocument document, Class<?> clazz, String prefix) throws Exception {
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

    private void saveConfigToFile(Object instance, ConfigMetadata metadata, String schemaVersion) throws Exception {
        ConfigDocument document = ConfigDocument.empty();
        saveFields(instance, document, instance.getClass(), "");
        migrator.applySchemaVersion(document, schemaVersion);
        File file = metadata.resolveFile(platform.configDir());
        ConfigDocument.ensureParentDirectory(file);
        document.save(file);
        flushExternalFields(instance, instance.getClass(), "");
    }

    /**
     * Writes every {@link SaveTo} field to its own file. Fields sharing a
     * {@code @SaveTo} path go into one document, keyed by the field's config path.
     */
    private void flushExternalFields(Object instance, Class<?> clazz, String prefix) throws Exception {
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
        warnIfMainThread("reload");
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
            if (reloadEntry(entry, sectionSet, false) && shouldNotifyOnChange(entry)) {
                notifyChangeListeners(entry, sectionSet);
            }
        }
    }

    /**
     * Reloads a configuration instance asynchronously.
     *
     * @param configInstance config instance to reload
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAsync(Object configInstance) {
        return CompletableFuture.runAsync(() -> reload(configInstance), scheduler.io());
    }

    /**
     * Reloads a configuration instance, auto-switching to async on sensitive threads.
     *
     * @param configInstance config instance
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadSmart(Object configInstance) {
        if (isBlockingSensitiveThread()) {
            return reloadAsync(configInstance);
        }
        reload(configInstance);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Reloads config instances for a class asynchronously.
     *
     * @param configClass config type
     * @param sections optional sections
     * @param <T> config type
     * @return future that completes after reload
     */
    public <T> CompletableFuture<Void> reloadAsync(Class<T> configClass, String... sections) {
        return CompletableFuture.runAsync(() -> reload(configClass, sections), scheduler.io());
    }

    /**
     * Reloads config instances for a class, auto-switching to async on sensitive threads.
     *
     * @param configClass config class
     * @param sections optional sections
     * @param <T> config type
     * @return future that completes after reload
     */
    public <T> CompletableFuture<Void> reloadSmart(Class<T> configClass, String... sections) {
        if (isBlockingSensitiveThread()) {
            return reloadAsync(configClass, sections);
        }
        reload(configClass, sections);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Reloads all registered configs asynchronously.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAllAsync() {
        return CompletableFuture.runAsync(this::reloadAll, scheduler.io());
    }

    /**
     * Reloads all registered configs, auto-switching to async on sensitive threads.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAllSmart() {
        if (isBlockingSensitiveThread()) {
            return reloadAllAsync();
        }
        reloadAll();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Registers a listener that fires when the config changes on disk.
     *
     * @param <T> config type
     * @param configClass config type to monitor
     * @param listener callback receiving updated instance and changed sections
     */
    public <T> void onChange(Class<T> configClass, BiConsumer<T, Set<String>> listener) {
        subscribeChanges(configClass, listener);
    }

    /**
     * Subscribes to config change notifications and returns an unregister handle.
     *
     * @param <T> config type
     * @param configClass config type to monitor
     * @param listener callback receiving updated instance and changed sections
     * @return subscription handle that removes the listener when closed
     */
    @SuppressWarnings("unchecked")
    public <T> ListenerSubscription subscribeChanges(Class<T> configClass, BiConsumer<T, Set<String>> listener) {
        Objects.requireNonNull(configClass, "configClass");
        Objects.requireNonNull(listener, "listener");

        CopyOnWriteArrayList<BiConsumer<Object, Set<String>>> listeners =
                (CopyOnWriteArrayList<BiConsumer<Object, Set<String>>>) changeListeners.computeIfAbsent(
                        configClass, k -> new CopyOnWriteArrayList<>());
        BiConsumer<Object, Set<String>> typedListener = (BiConsumer<Object, Set<String>>) listener;
        listeners.add(typedListener);
        return () -> {
            listeners.remove(typedListener);
            if (listeners.isEmpty()) {
                changeListeners.remove(configClass, listeners);
            }
        };
    }

    private void notifyChangeListeners(ConfigEntry<?> entry, Set<String> changedSections) {
        List<BiConsumer<Object, Set<String>>> listeners = changeListeners.get(entry.key.configClass);
        if (listeners == null) {
            return;
        }

        for (BiConsumer<Object, Set<String>> listener : listeners) {
            try {
                listener.accept(entry.instance, changedSections);
            } catch (Throwable error) {
                logger.error("Failed to notify config change listener for "
                        + entry.key.configClass.getName(), error);
            }
        }
    }

    private boolean shouldNotifyOnChange(ConfigEntry<?> entry) {
        ConfigReloadable reloadable = entry.key.configClass.getAnnotation(ConfigReloadable.class);
        return reloadable == null || reloadable.notifyOnChange();
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
        synchronized (watcherLock) {
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

                ConfigDocument document = ConfigDocument.empty();
                saveFields(entry.instance, document, entry.instance.getClass(), "");
                migrator.applySchemaVersion(document, entry.schemaVersion);

                ConfigDocument.ensureParentDirectory(entry.file);
                document.save(entry.file);
                flushExternalFields(entry.instance, entry.instance.getClass(), "");
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
                // Always mutate in-place: the instance reference returned from register()
                // is part of the public contract — consumers hold it and expect getter
                // calls to see live data without re-fetching.
                LoadResult loadResult = loadConfigWithRetry(entry.instance, entry.metadata, sections);
                entry.schemaVersion = loadResult.schemaVersion();

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
        if (watchServiceUnavailable) {
            return;
        }
        File file = entry.file;
        Path filePath = file.toPath().toAbsolutePath();
        Path directory = filePath.getParent();
        if (directory == null) {
            directory = filePath;
        }

        fileIndex.computeIfAbsent(filePath, key -> ConcurrentHashMap.newKeySet()).add(entry.key);

        try {
            ensureWatchService();
            synchronized (watcherLock) {
                if (!directoryWatchKeys.containsKey(directory)) {
                    Files.createDirectories(directory);

                    WatchKey watchKey = directory.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    directoryWatchKeys.put(directory, watchKey);
                }

                directoryRefCounts.merge(directory, 1, Integer::sum);
            }
            startWatcherThread();

        } catch (IOException e) {
            watchServiceUnavailable = true;
            if (!watchServiceWarned) {
                watchServiceWarned = true;
                logger.warn("Failed to register config watcher (disabling realtime reload): "
                        + entry.metadata.getFilePath(), e);
            }
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
            Set<Path> changedFiles = new HashSet<>();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path changed = directory.resolve((Path) event.context()).toAbsolutePath();
                changedFiles.add(changed);
            }

            for (Path changed : changedFiles) {
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
                synchronized (watcherLock) {
                    directoryWatchKeys.remove(directory);
                    directoryRefCounts.remove(directory);
                }
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

        ensureReloadExecutor();
        reloadExecutor.submit(() -> {
            try {
                synchronized (shutdownLock) {
                    if (shuttingDown) {
                        return;
                    }
                    // Skip reload if the file's mtime matches what we last
                    // saw — this drops spurious watcher events caused by
                    // initial registration writes (saveConfig defaults), and
                    // duplicate ENTRY_CREATE/ENTRY_MODIFY pairs the OS emits
                    // for the same physical save.
                    long observed = entry.file.exists() ? entry.file.lastModified() : -1L;
                    long lastKnown = entry.lastModified.get();
                    if (lastKnown > 0 && observed == lastKnown) {
                        return;
                    }
                    if (reloadEntry(entry, Collections.emptySet(), true)) {
                        notifyChangeListenersOnMain(entry, Collections.emptySet());
                    }
                }
            } finally {
                entry.reloadComplete();
            }
        });
    }

    private void notifyChangeListenersOnMain(ConfigEntry<?> entry, Set<String> sections) {
        Tasks.runOnMain(platform, () -> notifyChangeListeners(entry, sections)).whenComplete((ignored, error) -> {
            if (error != null) {
                logger.warn("Failed to notify config change listeners for " + entry.key.filePath, error);
            }
        });
    }

    private void warnIfMainThread(String action) {
        if (isBlockingSensitiveThread()) {
            logger.warn("ConfigManager." + action + " performs disk I/O. Consider using " + action
                    + "Async() to avoid main-thread stalls.");
        }
    }

    private boolean isBlockingSensitiveThread() {
        if (platform == null) {
            return false;
        }
        ThreadContext context = platform.threadContext();
        if (context == ThreadContext.UNKNOWN) {
            return platform.isMainThread();
        }
        return context.isBlockingSensitive();
    }

    /**
     * Stops the watcher service and releases associated resources.
     */
    public void shutdown() {
        synchronized (shutdownLock) {
            shuttingDown = true;
        }
        if (shutdownRegistrar != null && shutdownHook != null) {
            shutdownRegistrar.unregisterShutdownHook(shutdownHook);
        }

        // Stop the watcher first so no new external reloads are queued while
        // we drain the reload executor. Closing watchService also unblocks
        // watchService.take() inside watchLoop.
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

        // Drain reload executor gracefully — let in-flight tasks observe
        // shuttingDown under shutdownLock and skip their work, then exit.
        // We avoid shutdownNow() here so currently-running reloadEntry()
        // calls can release entry.monitor cleanly.
        if (reloadExecutor != null) {
            reloadExecutor.shutdown();
            try {
                if (!reloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reloadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reloadExecutor.shutdownNow();
            }
            reloadExecutor = null;
        }

        directoryWatchKeys.clear();
        directoryRefCounts.clear();
        fileIndex.clear();
    }

    private synchronized void ensureReloadExecutor() {
        if (reloadExecutor != null) {
            return;
        }
        reloadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MagicUtils-ConfigReload");
            thread.setDaemon(true);
            return thread;
        });
    }

    private record LoadResult(String schemaVersion, boolean migrated) {
    }

}
