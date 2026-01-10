package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Platform-agnostic configuration manager using annotation-driven mapping and Jackson (YAML/JSON/JSONC/TOML).
 */
public class ConfigManager {
    private final Platform platform;
    private final PlatformLogger logger;
    private final Map<ConfigKey, ConfigEntry<?>> configs = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<ConfigKey>> classIndex = new ConcurrentHashMap<>();
    private final Map<Object, ConfigKey> instanceIndex = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> primaryInstances = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<BiConsumer<Object, Set<String>>>> changeListeners = new ConcurrentHashMap<>();
    private final Map<Class<?>, MigrationChain> migrationChains = new ConcurrentHashMap<>();

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
    private static final String EXT_TOKEN = "{ext}";
    private static final String MIGRATION_VERSION_KEY = "config-version";
    private static final String GLOBAL_FORMAT_FILE = "magicutils.format";
    private static final String GLOBAL_FORMAT_PROPERTY = "magicutils.config.format";
    private static final String GLOBAL_FORMAT_ENV = "MAGICUTILS_CONFIG_FORMAT";
    private static final String DEFAULT_EXTENSION = "yml";
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("jsonc", "json", "yml", "yaml", "toml");

    /**
     * Creates a new ConfigManager for the provided platform.
     *
     * @param platform platform abstraction
     */
    @SuppressWarnings("this-escape")
    public ConfigManager(Platform platform) {
        this.platform = platform;
        this.logger = platform.logger();
        if (platform instanceof ShutdownHookRegistrar registrar) {
            registrar.registerShutdownHook(this::shutdown);
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
            FormatDecision formatDecision = resolveFormatDecision(configFile, placeholdersCopy);
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
        MigrationChain chain = migrationChains.computeIfAbsent(configClass, k -> new MigrationChain());
        for (ConfigMigration migration : migrations) {
            chain.register(migration, logger);
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
        MigrationChain chain = migrationChains.computeIfAbsent(configClass, k -> new MigrationChain());
        chain.register(migration, logger);
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
        return register(configClass, toPlaceholderMap(placeholders));
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
            if (reloadEntry(entry, Collections.emptySet(), false)) {
                ConfigReloadable reloadable = entry.key.configClass.getAnnotation(ConfigReloadable.class);
                if (reloadable != null && reloadable.notifyOnChange()) {
                    notifyChangeListeners(entry, Collections.emptySet());
                }
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
        File configFile = metadata.resolveFile(platform.configDir());
        boolean created = false;

        if (!configFile.exists() && metadata.isAutoCreate()) {
            createDefaultConfig(instance, configFile, metadata);
            created = true;
        }

        ConfigDocument document = ConfigDocument.load(configFile);
        MigrationResult migrationResult = applyMigrations(instance.getClass(), document, created);
        processFields(instance, document, instance.getClass());
        if (migrationResult.shouldSave()) {
            saveConfigToFile(instance, metadata, migrationResult.schemaVersion());
        }
        return new LoadResult(migrationResult.schemaVersion(), migrationResult.migrated());
    }

    private MigrationResult applyMigrations(Class<?> configClass, ConfigDocument document, boolean createdNew) {
        MigrationChain chain = migrationChains.get(configClass);
        if (chain == null || chain.isEmpty()) {
            return new MigrationResult(null, false, false);
        }
        Map<String, Object> data = document.data;
        String rawVersion = normalizeVersion(data.get(MIGRATION_VERSION_KEY));
        boolean versionMissing = rawVersion == null;
        String currentVersion = versionMissing ? "0" : rawVersion;
        boolean migrated = false;
        boolean changed = false;
        Set<String> visited = new HashSet<>();

        while (true) {
            if (!visited.add(currentVersion)) {
                logger.warn("Config migration cycle detected for " + configClass.getSimpleName()
                        + " at version " + currentVersion);
                break;
            }
            ConfigMigration migration = chain.byFrom.get(currentVersion);
            if (migration == null) {
                break;
            }
            migration.migrate(data);
            String nextVersion = normalizeVersion(migration.toVersion());
            if (nextVersion == null) {
                logger.warn("Migration from " + currentVersion + " returned empty target version for "
                        + configClass.getSimpleName());
                break;
            }
            currentVersion = nextVersion;
            migrated = true;
            changed = true;
        }

        if (migrated) {
            data.put(MIGRATION_VERSION_KEY, currentVersion);
        } else if (versionMissing && createdNew && chain.latestVersion != null) {
            currentVersion = chain.latestVersion;
            data.put(MIGRATION_VERSION_KEY, currentVersion);
            changed = true;
        } else if (versionMissing && !createdNew && chain.latestVersion != null) {
            logger.warn("Config " + configClass.getSimpleName() + " is missing " + MIGRATION_VERSION_KEY
                    + ". Add a migration from version 0 or set the version explicitly.");
        } else if (rawVersion != null && chain.latestVersion != null && !rawVersion.equals(chain.latestVersion)) {
            logger.warn("Config " + configClass.getSimpleName() + " is at version " + rawVersion
                    + " but latest is " + chain.latestVersion + ". Missing migration?");
        }

        return new MigrationResult(currentVersion, changed, migrated);
    }

    private void applySchemaVersion(ConfigDocument document, String schemaVersion) {
        if (document == null) {
            return;
        }
        String normalized = normalizeVersion(schemaVersion);
        if (normalized == null) {
            return;
        }
        document.set(MIGRATION_VERSION_KEY, normalized, null);
    }

    private static String normalizeVersion(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            long asLong = number.longValue();
            double asDouble = number.doubleValue();
            if (asDouble == (double) asLong) {
                return String.valueOf(asLong);
            }
            return String.valueOf(asDouble);
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static void ensureParentDirectory(File file) throws IOException {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Failed to create directory " + parent);
        }
    }

    private <T> void createDefaultConfig(T instance, File configFile, ConfigMetadata metadata) throws Exception {
        ensureParentDirectory(configFile);

        ConfigDocument document = ConfigDocument.empty();

        Comment classComment = instance.getClass().getAnnotation(Comment.class);
        if (classComment != null) {
            document.setHeader(Arrays.asList(classComment.value().split("\n")));
        }

        writeDefaults(instance, document, instance.getClass(), "");
        document.save(configFile);
        logger.info("Created default config: " + configFile.getName());
    }

    private void writeDefaults(Object instance, ConfigDocument document, Class<?> clazz, String prefix) throws Exception {
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

    private void processFields(Object instance, ConfigDocument document, Class<?> clazz) throws Exception {
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
                ConfigSectionView subsection = document.getConfigurationSection(path);
                if (subsection != null) {
                    processFields(sectionInstance, new ConfigDocument(subsection.unwrap(), document.header), field.getType());
                }
                continue;
            }

            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                loadFieldValue(instance, field, document, path);
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
                ParameterizedType listType = (ParameterizedType) field.getGenericType();
                Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];

                List<?> list = (List<?>) value;
                if (elementType.isAnnotationPresent(ConfigSerializable.class)) {
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
                    ConfigValueAdapter<?> adapter = ConfigAdapters.get(elementType);
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
                ParameterizedType mapType = (ParameterizedType) field.getGenericType();
                Class<?> keyType = (Class<?>) mapType.getActualTypeArguments()[0];
                Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];

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
            ParameterizedType listType = (ParameterizedType) field.getGenericType();
            Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];
            if (elementType.isAnnotationPresent(ConfigSerializable.class)) {
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

            ConfigValueAdapter<?> adapter = ConfigAdapters.get(elementType);
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
            ParameterizedType mapType = (ParameterizedType) field.getGenericType();
            Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];
            if (valueType.isAnnotationPresent(ConfigSerializable.class)) {
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

            ConfigValueAdapter<?> adapter = ConfigAdapters.get(valueType);
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

    private Map<String, String> toPlaceholderMap(String... placeholders) {
        Map<String, String> map = new LinkedHashMap<>();
        if (placeholders == null || placeholders.length == 0) {
            return map;
        }
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be key/value pairs");
        }
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return map;
    }

    private String applyPlaceholders(String path, Map<String, String> placeholders) {
        if (path == null || placeholders == null || placeholders.isEmpty()) {
            return path;
        }
        String resolved = path;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    private FormatDecision resolveFormatDecision(ConfigFile configFile, Map<String, String> placeholders) {
        String template = applyPlaceholders(configFile.value(), placeholders);
        boolean hasExtToken = template.contains(EXT_TOKEN);
        String defaultExt = null;
        String detectionPath = template;

        if (hasExtToken) {
            defaultExt = normalizeExtension(placeholders != null ? placeholders.get("ext") : null);
            if (defaultExt == null) {
                defaultExt = resolveDefaultExtension();
            }
            detectionPath = template.replace(EXT_TOKEN, defaultExt);
        }

        ExtensionInfo extensionInfo = extractExtension(detectionPath);
        if (extensionInfo == null) {
            String fixedPath = hasExtToken ? detectionPath : template;
            return FormatDecision.fixed(fixedPath);
        }

        String basePath = extensionInfo.basePath;
        if (defaultExt == null) {
            defaultExt = extensionInfo.extension;
        }

        Map<String, Path> existing = findExistingFormats(basePath);
        String preferredExt = resolvePreferredFormat(basePath);
        String targetExt;
        String sourceExt = null;

        if (preferredExt != null) {
            targetExt = preferredExt;
            if (!existing.containsKey(preferredExt) && !existing.isEmpty()) {
                sourceExt = chooseMigrationSource(existing);
            }
        } else if (existing.containsKey(defaultExt)) {
            targetExt = defaultExt;
        } else if (existing.size() == 1) {
            targetExt = existing.keySet().iterator().next();
        } else if (!existing.isEmpty()) {
            targetExt = chooseByPriority(existing.keySet(), defaultExt);
            logger.warn("Multiple config formats found for '" + basePath + "'. Using '" + targetExt
                    + "'. Add '" + basePath + ".format' to pick a default.");
        } else {
            targetExt = defaultExt;
        }

        String targetPath = buildPath(template, basePath, hasExtToken, targetExt);
        String sourcePath = sourceExt != null ? buildPath(template, basePath, hasExtToken, sourceExt) : null;
        return new FormatDecision(targetPath, sourcePath, targetExt, sourceExt, true);
    }

    private String resolveDefaultExtension() {
        if (platform instanceof ConfigFormatProvider provider) {
            String fromPlatform = normalizeExtension(provider.defaultConfigExtension());
            if (fromPlatform != null) {
                return fromPlatform;
            }
        }
        ConfigFormat fallback = ConfigFormat.defaultFormat();
        return fallback == ConfigFormat.YAML ? DEFAULT_EXTENSION : "jsonc";
    }

    private String resolvePreferredFormat(String basePath) {
        String fromFile = readFormatFile(platform.configDir().resolve(basePath + ".format"));
        if (fromFile != null) {
            return fromFile;
        }
        String fromProperty = normalizeExtension(System.getProperty(GLOBAL_FORMAT_PROPERTY));
        if (fromProperty != null) {
            return fromProperty;
        }
        String fromEnv = normalizeExtension(System.getenv(GLOBAL_FORMAT_ENV));
        if (fromEnv != null) {
            return fromEnv;
        }
        return readFormatFile(platform.configDir().resolve(GLOBAL_FORMAT_FILE));
    }

    private String readFormatFile(Path formatFile) {
        if (formatFile == null || !Files.exists(formatFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(formatFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                String normalized = normalizeExtension(trimmed);
                if (normalized == null) {
                    logger.warn("Unsupported config format '" + trimmed + "' in " + formatFile.getFileName());
                }
                return normalized;
            }
        } catch (IOException e) {
            logger.warn("Failed to read config format file: " + formatFile, e);
        }
        return null;
    }

    private String normalizeExtension(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        ConfigFormat format = ConfigFormat.fromExtension(normalized);
        if (format == null) {
            return null;
        }
        if (!format.isAvailable()) {
            logger.warn("Config format '" + normalized + "' is not available. " + format.unavailableMessage());
            return null;
        }
        return normalized;
    }

    private ExtensionInfo extractExtension(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot >= path.length() - 1) {
            return null;
        }
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return null;
        }
        String basePath = path.substring(0, dot);
        return new ExtensionInfo(basePath, ext);
    }

    private String buildPath(String template, String basePath, boolean hasExtToken, String ext) {
        if (hasExtToken) {
            return template.replace(EXT_TOKEN, ext);
        }
        return basePath + "." + ext;
    }

    private Map<String, Path> findExistingFormats(String basePath) {
        Map<String, Path> existing = new LinkedHashMap<>();
        for (String ext : SUPPORTED_EXTENSIONS) {
            Path candidate = platform.configDir().resolve(basePath + "." + ext);
            if (Files.exists(candidate)) {
                ConfigFormat format = ConfigFormat.fromExtension(ext);
                if (format != null && !format.isAvailable()) {
                    logger.warn("Config '" + candidate.getFileName() + "' uses unavailable format. "
                            + format.unavailableMessage());
                    continue;
                }
                existing.put(ext, candidate);
            }
        }
        return existing;
    }

    private String chooseByPriority(Set<String> existing, String defaultExt) {
        if (defaultExt != null && existing.contains(defaultExt)) {
            return defaultExt;
        }
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (existing.contains(ext)) {
                return ext;
            }
        }
        return existing.iterator().next();
    }

    private String chooseMigrationSource(Map<String, Path> existing) {
        String chosen = null;
        long newest = Long.MIN_VALUE;
        for (Map.Entry<String, Path> entry : existing.entrySet()) {
            try {
                long modified = Files.getLastModifiedTime(entry.getValue()).toMillis();
                if (modified > newest) {
                    newest = modified;
                    chosen = entry.getKey();
                }
            } catch (IOException ignored) {
                if (chosen == null) {
                    chosen = entry.getKey();
                }
            }
        }
        return chosen != null ? chosen : existing.keySet().iterator().next();
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
        applySchemaVersion(document, schemaVersion);
        File file = metadata.resolveFile(platform.configDir());
        ensureParentDirectory(file);
        document.save(file);
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
            if (reloadEntry(entry, sectionSet, false) && reloadable != null && reloadable.notifyOnChange()) {
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
        return CompletableFuture.runAsync(() -> reload(configInstance));
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
        return CompletableFuture.runAsync(() -> reload(configClass, sections));
    }

    /**
     * Reloads all registered configs asynchronously.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAllAsync() {
        return CompletableFuture.runAsync(this::reloadAll);
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
                applySchemaVersion(document, entry.schemaVersion);

                ensureParentDirectory(entry.file);
                document.save(entry.file);
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
                LoadResult loadResult = loadConfig(entry.instance, entry.metadata);
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
                if (reloadEntry(entry, Collections.emptySet(), true)) {
                    platform.runOnMain(() -> notifyChangeListeners(entry, Collections.emptySet()));
                }
            } finally {
                entry.reloadComplete();
            }
        });
    }

    private void warnIfMainThread(String action) {
        if (platform != null && platform.isMainThread()) {
            logger.warn("ConfigManager." + action + " performs disk I/O. Consider using " + action
                    + "Async() to avoid main-thread stalls.");
        }
    }

    /**
     * Stops the watcher service and releases associated resources.
     */
    public void shutdown() {
        shuttingDown = true;

        if (reloadExecutor != null) {
            reloadExecutor.shutdownNow();
            try {
                reloadExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reloadExecutor = null;
        }

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

    private record ExtensionInfo(String basePath, String extension) {
    }

    private record FormatDecision(String targetPath, String sourcePath,
                                  String targetExt, String sourceExt,
                                  boolean formatAware) {
        static FormatDecision fixed(String targetPath) {
            return new FormatDecision(targetPath, null, null, null, false);
        }
    }

    private record LoadResult(String schemaVersion, boolean migrated) {
    }

    private record MigrationResult(String schemaVersion, boolean shouldSave, boolean migrated) {
    }

    private static final class MigrationChain {
        private final Map<String, ConfigMigration> byFrom = new LinkedHashMap<>();
        private String latestVersion;

        private boolean isEmpty() {
            return byFrom.isEmpty();
        }

        private void register(ConfigMigration migration, PlatformLogger logger) {
            if (migration == null) {
                return;
            }
            String from = normalizeVersion(migration.fromVersion());
            String to = normalizeVersion(migration.toVersion());
            if (from == null || to == null) {
                if (logger != null) {
                    logger.warn("Skipping migration with empty version identifiers: " + migration.getClass().getName());
                }
                return;
            }
            if (byFrom.containsKey(from) && logger != null) {
                logger.warn("Replacing migration for version " + from + " with " + migration.getClass().getName());
            }
            byFrom.put(from, migration);
            recomputeLatest(logger);
        }

        private void recomputeLatest(PlatformLogger logger) {
            if (byFrom.isEmpty()) {
                latestVersion = null;
                return;
            }
            Set<String> fromVersions = new LinkedHashSet<>(byFrom.keySet());
            Set<String> toVersions = new LinkedHashSet<>();
            for (ConfigMigration migration : byFrom.values()) {
                String to = normalizeVersion(migration.toVersion());
                if (to != null) {
                    toVersions.add(to);
                }
            }
            Set<String> endCandidates = new LinkedHashSet<>(toVersions);
            endCandidates.removeAll(fromVersions);
            if (endCandidates.size() == 1) {
                latestVersion = endCandidates.iterator().next();
                return;
            }
            if (!toVersions.isEmpty()) {
                latestVersion = new ArrayList<>(toVersions).get(toVersions.size() - 1);
                if (logger != null && endCandidates.size() > 1) {
                    logger.warn("Multiple migration endpoints detected. Using version " + latestVersion);
                }
                return;
            }
            latestVersion = null;
        }
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
        private String schemaVersion;
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

    private static final class ConfigMetadata {
        @Getter
        private final String filePath;
        @Getter
        private final boolean autoCreate;
        @Getter
        private final String templatePath;

        ConfigMetadata(String filePath, boolean autoCreate, String templatePath, Path baseDir) {
            this.filePath = sanitizePath(filePath, baseDir);
            this.autoCreate = autoCreate;
            this.templatePath = templatePath;
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
     * Minimal config document helper backed by Jackson.
     */
    private static class ConfigDocument extends ConfigSectionView {
        private static final ObjectMapper SCALAR_MAPPER = JsonMapper.builder().build();
        private final Map<String, Object> data;
        private final Map<String, List<String>> comments;
        private List<String> header;

        private ConfigDocument(Map<String, Object> backing, List<String> header) {
            super(backing);
            this.data = backing;
            this.header = header;
            this.comments = new LinkedHashMap<>();
        }

        static ConfigDocument empty() {
            return new ConfigDocument(new LinkedHashMap<>(), null);
        }

        static ConfigDocument load(File file) throws IOException {
            if (file == null || !file.exists()) {
                return empty();
            }
            ConfigFormat format = ConfigFormat.fromFile(file);
            Map<String, Object> map = format.read(file);
            return new ConfigDocument(map, null);
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

        void setHeader(List<String> lines) {
            this.header = lines;
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

        ConfigSectionView getConfigurationSection(String path) {
            NavigateResult res = navigate(data, path);
            if (res.found && res.value instanceof Map) {
                return new ConfigSectionView(castMap((Map<?, ?>) res.value));
            }
            return null;
        }

        Object get(String path) {
            NavigateResult res = navigate(data, path);
            return res.found ? res.value : null;
        }

        void save(File file) throws IOException {
            ConfigFormat.fromFile(file).write(file, data, header, comments);
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

        private static void writeYamlWithComments(File file,
                                                  Map<String, Object> map,
                                                  List<String> header,
                                                  Map<String, List<String>> comments) throws IOException {
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                if (header != null && !header.isEmpty()) {
                    for (String line : header) {
                        writer.write("# " + line + System.lineSeparator());
                    }
                }
                writeWithComments(writer, map, comments, "", 0);
            }
        }

        private static void writeJsonWithComments(File file,
                                                  Map<String, Object> map,
                                                  List<String> header,
                                                  Map<String, List<String>> comments) throws IOException {
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                if (header != null && !header.isEmpty()) {
                    for (String line : header) {
                        writer.write("// " + line + System.lineSeparator());
                    }
                }
                writeJsonObject(writer, map, comments, "", 0);
                writer.write(System.lineSeparator());
            }
        }

        private static void writeTomlWithComments(File file,
                                                  Map<String, Object> map,
                                                  List<String> header,
                                                  Map<String, List<String>> comments) throws IOException {
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                if (header != null && !header.isEmpty()) {
                    for (String line : header) {
                        writer.write("# " + line + System.lineSeparator());
                    }
                }
                writeTomlTableBody(writer, map, comments, "");
            }
        }

        private static void writeWithComments(Writer writer,
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
                    writeWithComments(writer, castMap((Map<?, ?>) value), comments, currentPath, indentLevel + 1);
                } else if (value instanceof List) {
                    writer.write(indent + key + ":" + System.lineSeparator());
                    writeList(writer, (List<?>) value, comments, currentPath, indentLevel + 1);
                } else if (value instanceof String str && str.contains("\n")) {
                    writer.write(indent + key + ": |" + System.lineSeparator());
                    writeMultilineValue(writer, str, indentLevel + 1);
                } else {
                    writer.write(indent + key + ": " + formatScalar(value) + System.lineSeparator());
                }
            }
        }

        private static void writeJsonObject(Writer writer,
                                            Map<String, Object> map,
                                            Map<String, List<String>> comments,
                                            String pathPrefix,
                                            int indentLevel) throws IOException {
            writer.write("{");
            if (map.isEmpty()) {
                writer.write("}");
                return;
            }
            writer.write(System.lineSeparator());
            int index = 0;
            int size = map.size();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
                List<String> commentLines = comments != null ? comments.get(currentPath) : null;
                writeCommentLines(writer, commentLines, "// ", indentLevel + 1);
                String indent = "  ".repeat(indentLevel + 1);
                writer.write(indent + formatJsonKey(key) + ": ");
                writeJsonValue(writer, entry.getValue(), comments, currentPath, indentLevel + 1);
                if (index < size - 1) {
                    writer.write(",");
                }
                writer.write(System.lineSeparator());
                index++;
            }
            writer.write("  ".repeat(indentLevel) + "}");
        }

        private static void writeJsonArray(Writer writer,
                                           List<?> list,
                                           Map<String, List<String>> comments,
                                           String pathPrefix,
                                           int indentLevel) throws IOException {
            writer.write("[");
            if (list.isEmpty()) {
                writer.write("]");
                return;
            }
            writer.write(System.lineSeparator());
            for (int i = 0; i < list.size(); i++) {
                Object elem = list.get(i);
                String currentPath = pathPrefix + "[" + i + "]";
                List<String> commentLines = comments != null ? comments.get(currentPath) : null;
                writeCommentLines(writer, commentLines, "// ", indentLevel + 1);
                String indent = "  ".repeat(indentLevel + 1);
                writer.write(indent);
                writeJsonValue(writer, elem, comments, currentPath, indentLevel + 1);
                if (i < list.size() - 1) {
                    writer.write(",");
                }
                writer.write(System.lineSeparator());
            }
            writer.write("  ".repeat(indentLevel) + "]");
        }

        private static void writeJsonValue(Writer writer,
                                           Object value,
                                           Map<String, List<String>> comments,
                                           String pathPrefix,
                                           int indentLevel) throws IOException {
            if (value instanceof Map) {
                writeJsonObject(writer, castMap((Map<?, ?>) value), comments, pathPrefix, indentLevel);
            } else if (value instanceof List) {
                writeJsonArray(writer, (List<?>) value, comments, pathPrefix, indentLevel);
            } else {
                writer.write(formatScalar(value));
            }
        }

        private static void writeTomlTable(Writer writer,
                                           Map<String, Object> map,
                                           Map<String, List<String>> comments,
                                           String pathPrefix) throws IOException {
            if (!pathPrefix.isEmpty()) {
                List<String> commentLines = comments != null ? comments.get(pathPrefix) : null;
                writeCommentLines(writer, commentLines, "# ", 0);
                writer.write("[" + formatTomlPath(pathPrefix) + "]" + System.lineSeparator());
            }
            writeTomlTableBody(writer, map, comments, pathPrefix);
        }

        private static void writeTomlArrayTable(Writer writer,
                                                List<?> list,
                                                Map<String, List<String>> comments,
                                                String pathPrefix) throws IOException {
            if (list.isEmpty()) {
                return;
            }
            boolean first = true;
            for (int i = 0; i < list.size(); i++) {
                Object elem = list.get(i);
                if (!(elem instanceof Map)) {
                    continue;
                }
                if (first) {
                    List<String> commentLines = comments != null ? comments.get(pathPrefix) : null;
                    writeCommentLines(writer, commentLines, "# ", 0);
                }
                writer.write("[[" + formatTomlPath(pathPrefix) + "]]" + System.lineSeparator());
                writeTomlTableBody(writer, castMap((Map<?, ?>) elem), comments, pathPrefix);
                if (i < list.size() - 1) {
                    writer.write(System.lineSeparator());
                }
                first = false;
            }
        }

        private static void writeTomlTableBody(Writer writer,
                                               Map<String, Object> map,
                                               Map<String, List<String>> comments,
                                               String pathPrefix) throws IOException {
            Map<String, Map<String, Object>> tables = new LinkedHashMap<>();
            Map<String, List<?>> arrayTables = new LinkedHashMap<>();
            boolean wroteValue = false;

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Map) {
                    tables.put(key, castMap((Map<?, ?>) value));
                    continue;
                }
                if (value instanceof List<?> list && isListOfMaps(list)) {
                    arrayTables.put(key, list);
                    continue;
                }
                String formatted = formatTomlValue(value);
                if (formatted == null) {
                    continue;
                }
                String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
                List<String> commentLines = comments != null ? comments.get(currentPath) : null;
                writeCommentLines(writer, commentLines, "# ", 0);
                writer.write(formatTomlKey(key) + " = " + formatted + System.lineSeparator());
                wroteValue = true;
            }

            boolean wroteBlock = wroteValue;
            for (Map.Entry<String, Map<String, Object>> entry : tables.entrySet()) {
                if (wroteBlock) {
                    writer.write(System.lineSeparator());
                }
                String tablePath = pathPrefix.isEmpty() ? entry.getKey() : pathPrefix + "." + entry.getKey();
                writeTomlTable(writer, entry.getValue(), comments, tablePath);
                wroteBlock = true;
            }
            for (Map.Entry<String, List<?>> entry : arrayTables.entrySet()) {
                if (wroteBlock) {
                    writer.write(System.lineSeparator());
                }
                String tablePath = pathPrefix.isEmpty() ? entry.getKey() : pathPrefix + "." + entry.getKey();
                writeTomlArrayTable(writer, entry.getValue(), comments, tablePath);
                wroteBlock = true;
            }
        }

        private static void writeList(Writer writer, List<?> list,
                                      Map<String, List<String>> comments,
                                      String pathPrefix,
                                      int indentLevel) throws IOException {
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
                    writeWithComments(writer, castMap((Map<?, ?>) elem), comments, currentPath, indentLevel + 1);
                } else if (elem instanceof List) {
                    writer.write(indent + "- " + System.lineSeparator());
                    writeList(writer, (List<?>) elem, comments, currentPath, indentLevel + 1);
                } else if (elem instanceof String str && str.contains("\n")) {
                    writer.write(indent + "- |" + System.lineSeparator());
                    writeMultilineValue(writer, str, indentLevel + 2);
                } else {
                    writer.write(indent + "- " + formatScalar(elem) + System.lineSeparator());
                }
            }
        }

        private static void writeMultilineValue(Writer writer, String value, int indentLevel) throws IOException {
            String indent = "  ".repeat(indentLevel);
            String[] lines = value.split("\\r?\\n", -1);
            for (String line : lines) {
                writer.write(indent + line + System.lineSeparator());
            }
        }

        private static void writeCommentLines(Writer writer,
                                              List<String> lines,
                                              String prefix,
                                              int indentLevel) throws IOException {
            if (lines == null || lines.isEmpty()) {
                return;
            }
            String indent = "  ".repeat(indentLevel);
            for (String line : lines) {
                writer.write(indent + prefix + line + System.lineSeparator());
            }
        }

        private static boolean isListOfMaps(List<?> list) {
            if (list == null || list.isEmpty()) {
                return false;
            }
            for (Object item : list) {
                if (!(item instanceof Map)) {
                    return false;
                }
            }
            return true;
        }

        private static String formatTomlValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Map) {
                return null;
            }
            if (value instanceof List<?> list) {
                List<String> parts = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        return null;
                    }
                    String formatted = formatTomlValue(item);
                    if (formatted != null) {
                        parts.add(formatted);
                    }
                }
                return "[" + String.join(", ", parts) + "]";
            }
            return formatScalar(value);
        }

        private static String formatJsonKey(String key) {
            if (key == null) {
                return "\"\"";
            }
            return formatScalar(key);
        }

        private static String formatTomlKey(String key) {
            if (key == null) {
                return "\"\"";
            }
            return formatScalar(key);
        }

        private static String formatTomlPath(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            String[] parts = path.split("\\.");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    builder.append('.');
                }
                builder.append(formatTomlKey(parts[i]));
            }
            return builder.toString();
        }

        private static String formatScalar(Object value) {
            if (value == null) {
                return "null";
            }
            try {
                return SCALAR_MAPPER.writeValueAsString(value).trim();
            } catch (JsonProcessingException e) {
                return String.valueOf(value);
            }
        }
    }

    private enum ConfigFormat {
        YAML(Set.of("yml", "yaml"), "YAML",
                "com.fasterxml.jackson.dataformat.yaml.YAMLFactory",
                "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml",
                ConfigFormat::createYamlMapper),
        JSON(Set.of("json"), "JSON", null, null, ConfigFormat::createJsonMapper),
        JSONC(Set.of("jsonc"), "JSONC", null, null, ConfigFormat::createJsonCMapper),
        TOML(Set.of("toml"), "TOML",
                "com.fasterxml.jackson.dataformat.toml.TomlFactory",
                "com.fasterxml.jackson.dataformat:jackson-dataformat-toml",
                ConfigFormat::createTomlMapper);

        private final Set<String> extensions;
        private final String displayName;
        private final String requiredClass;
        private final String dependency;
        private final Supplier<ObjectMapper> mapperSupplier;
        private volatile ObjectMapper mapper;

        ConfigFormat(Set<String> extensions, String displayName, String requiredClass, String dependency,
                     Supplier<ObjectMapper> mapperSupplier) {
            this.extensions = extensions;
            this.displayName = displayName;
            this.requiredClass = requiredClass;
            this.dependency = dependency;
            this.mapperSupplier = mapperSupplier;
        }

        static ConfigFormat fromFile(File file) {
            if (file == null) {
                return defaultFormat();
            }
            String name = file.getName().toLowerCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || dot == name.length() - 1) {
                return defaultFormat();
            }
            String ext = name.substring(dot + 1);
            ConfigFormat format = fromExtension(ext);
            return format != null ? format : defaultFormat();
        }

        static ConfigFormat fromExtension(String extension) {
            if (extension == null) {
                return null;
            }
            for (ConfigFormat format : values()) {
                if (format.extensions.contains(extension)) {
                    return format;
                }
            }
            return null;
        }

        boolean isAvailable() {
            return requiredClass == null || isClassPresent(resolveJacksonClass(requiredClass));
        }

        String unavailableMessage() {
            if (dependency == null) {
                return displayName + " support is not available.";
            }
            return displayName + " support is not available (missing " + dependency + ").";
        }

        Map<String, Object> read(File file) throws IOException {
            if (file == null || !file.exists() || file.length() == 0) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> map = mapper().readValue(file, new TypeReference<>() {});
            return map == null ? new LinkedHashMap<>() : ConfigDocument.castMap(map);
        }

        void write(File file, Map<String, Object> data, List<String> header,
                   Map<String, List<String>> comments) throws IOException {
            ensureParentDirectory(file);
            if (this == YAML) {
                ConfigDocument.writeYamlWithComments(file, data, header, comments);
                return;
            }
            if (this == JSONC) {
                ConfigDocument.writeJsonWithComments(file, data, header, comments);
                return;
            }
            if (this == TOML) {
                ConfigDocument.writeTomlWithComments(file, data, header, comments);
                return;
            }
            ObjectWriter writer = mapper().writerWithDefaultPrettyPrinter();
            writer.writeValue(file, data);
        }

        private ObjectMapper mapper() {
            ObjectMapper local = mapper;
            if (local != null) {
                return local;
            }
            if (!isAvailable()) {
                throw new IllegalStateException(unavailableMessage());
            }
            synchronized (this) {
                local = mapper;
                if (local == null) {
                    try {
                        local = mapperSupplier.get();
                    } catch (RuntimeException | LinkageError e) {
                        throw new IllegalStateException(initFailedMessage(), e);
                    }
                    mapper = local;
                }
            }
            return local;
        }

        private String initFailedMessage() {
            if (dependency == null) {
                return displayName + " support failed to initialize.";
            }
            return displayName + " support failed to initialize. Ensure " + dependency + " is on the classpath.";
        }

        private static ConfigFormat defaultFormat() {
            return YAML.isAvailable() ? YAML : JSONC;
        }

        private static String resolveJacksonClass(String className) {
            if (className == null) {
                return null;
            }
            String relocated = className.replace("com.fasterxml.jackson.", "dev.ua.theroer.magicutils.libs.jackson.");
            if (!relocated.equals(className) && isClassPresent(relocated)) {
                return relocated;
            }
            return className;
        }

        private static boolean isClassPresent(String name) {
            try {
                Class.forName(name, false, ConfigManager.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        private static ObjectMapper createYamlMapper() {
            JsonFactory factory = buildYamlFactory();
            ObjectMapper mapper = new ObjectMapper(factory);
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper;
        }

        private static ObjectMapper createJsonMapper() {
            return JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .build();
        }

        private static ObjectMapper createJsonCMapper() {
            return JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                    .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                    .build();
        }

        private static ObjectMapper createTomlMapper() {
            JsonFactory factory = buildFactory("com.fasterxml.jackson.dataformat.toml.TomlFactory");
            ObjectMapper mapper = new ObjectMapper(factory);
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper;
        }

        private static JsonFactory buildYamlFactory() {
            try {
                Class<?> factoryClass = Class.forName(resolveJacksonClass("com.fasterxml.jackson.dataformat.yaml.YAMLFactory"));
                Object builder = invokeStatic(factoryClass, "builder");
                if (builder != null) {
                    disableYamlDocStart(builder);
                    Object built = invokeNoArgs(builder, "build");
                    if (built instanceof JsonFactory) {
                        return (JsonFactory) built;
                    }
                }
                Object factory = factoryClass.getDeclaredConstructor().newInstance();
                return (JsonFactory) factory;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("YAML support failed to initialize.", e);
            }
        }

        private static JsonFactory buildFactory(String className) {
            try {
                Class<?> factoryClass = Class.forName(resolveJacksonClass(className));
                Object factory = factoryClass.getDeclaredConstructor().newInstance();
                return (JsonFactory) factory;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to initialize " + className + ".", e);
            }
        }

        private static Object invokeStatic(Class<?> type, String methodName) {
            try {
                return type.getMethod(methodName).invoke(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static Object invokeNoArgs(Object target, String methodName) {
            if (target == null) {
                return null;
            }
            try {
                return target.getClass().getMethod(methodName).invoke(target);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static void disableYamlDocStart(Object builder) {
            if (builder == null) {
                return;
            }
            try {
                Class<?> featureClass = Class.forName(resolveJacksonClass("com.fasterxml.jackson.dataformat.yaml.YAMLGenerator$Feature"));
                Object feature = Enum.valueOf((Class<Enum>) featureClass, "WRITE_DOC_START_MARKER");
                builder.getClass().getMethod("disable", featureClass).invoke(builder, feature);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    /**
     * Lightweight view over a configuration section (backed by a map).
     */
    private static class ConfigSectionView {
        private final Map<String, Object> backing;

        ConfigSectionView(Map<String, Object> backing) {
            this.backing = backing;
        }

        Map<String, Object> unwrap() {
            return backing;
        }
    }
}
