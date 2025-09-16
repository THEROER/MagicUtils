package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import lombok.Getter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Manager for handling configuration files with annotations.
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<Class<?>, Object> registeredConfigs = new ConcurrentHashMap<>();
    private final Map<Class<?>, ConfigMetadata> configMetadata = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<BiConsumer<Object, Set<String>>>> changeListeners = new ConcurrentHashMap<>();

    /**
     * Creates a new ConfigManager.
     * 
     * @param plugin the plugin instance
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
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
            // Check for @ConfigFile annotation
            ConfigFile configFile = configClass.getAnnotation(ConfigFile.class);
            if (configFile == null) {
                throw new IllegalArgumentException(
                        InternalMessages.ERR_MISSING_CONFIGFILE.get("class", configClass.getName()));
            }

            // Create instance
            T instance = configClass.getDeclaredConstructor().newInstance();

            // Store metadata with resolved path
            ConfigMetadata metadata = new ConfigMetadata(configFile, placeholders);
            configMetadata.put(configClass, metadata);

            // Load configuration
            loadConfig(instance, metadata);

            // Register instance
            registeredConfigs.put(configClass, instance);

            return instance;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register config: " + configClass.getName(), e);
            throw new RuntimeException("Failed to register config", e);
        }
    }

    /**
     * Loads configuration values into the instance.
     */
    private <T> void loadConfig(T instance, ConfigMetadata metadata) throws Exception {
        File configFile = new File(plugin.getDataFolder(), metadata.getFilePath());

        // Create file if needed
        if (!configFile.exists() && metadata.isAutoCreate()) {
            createDefaultConfig(instance, configFile, metadata);
        }

        // Load YAML
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        // Process fields
        processFields(instance, yaml, instance.getClass());
    }

    /**
     * Creates default configuration file.
     */
    private <T> void createDefaultConfig(T instance, File configFile, ConfigMetadata metadata) throws Exception {
        configFile.getParentFile().mkdirs();

        YamlConfiguration yaml = new YamlConfiguration();

        // Add header comment if present
        Comment classComment = instance.getClass().getAnnotation(Comment.class);
        if (classComment != null) {
            yaml.options().setHeader(Arrays.asList(classComment.value().split("\n")));
        }

        // Process fields for defaults
        writeDefaults(instance, yaml, instance.getClass(), "");

        // Save file
        yaml.save(configFile);
        plugin.getLogger().info(InternalMessages.SYS_CREATED_DEFAULT_CONFIG.get("file", configFile.getName()));
    }

    /**
     * Writes default values to YAML.
     */
    private void writeDefaults(Object instance, YamlConfiguration yaml, Class<?> clazz, String prefix)
            throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            // Skip non-config fields
            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);

            // Handle @ConfigSection
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                Object sectionInstance = field.get(instance);
                if (sectionInstance == null) {
                    // Check for default value first
                    Object defaultValue = getDefaultValue(field);
                    if (defaultValue != null) {
                        sectionInstance = defaultValue;
                    } else {
                        sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                    }
                    field.set(instance, sectionInstance);
                }
                // Check if section is ConfigSerializable
                if (field.getType().isAnnotationPresent(ConfigSerializable.class)) {
                    // Serialize the entire object
                    Map<String, Object> serialized = ConfigSerializer.serialize(sectionInstance);
                    for (Map.Entry<String, Object> entry : serialized.entrySet()) {
                        yaml.set(path + "." + entry.getKey(), entry.getValue());
                    }
                } else {
                    // Regular recursive handling
                    writeDefaults(sectionInstance, yaml, field.getType(), path + ".");
                }
                continue;
            }

            // Handle @ConfigValue
            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                // Get default value
                Object defaultValue = getDefaultValue(field);
                if (defaultValue != null) {
                    // Serialize ConfigSerializable objects before saving
                    Object valueToSave = defaultValue;

                    // Handle List with ConfigSerializable values
                    if (defaultValue instanceof List && field.getType().equals(List.class)) {
                        ParameterizedType listType = (ParameterizedType) field.getGenericType();
                        Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];

                        if (elementType.isAnnotationPresent(ConfigSerializable.class)) {
                            List<Map<String, Object>> serializedList = new ArrayList<>();
                            @SuppressWarnings("unchecked")
                            List<Object> list = (List<Object>) defaultValue;

                            for (Object item : list) {
                                if (item != null) {
                                    serializedList.add(ConfigSerializer.serialize(item));
                                }
                            }
                            valueToSave = serializedList;
                        }
                    }
                    // Handle Map with ConfigSerializable values
                    else if (defaultValue instanceof Map && field.getType().equals(Map.class)) {
                        ParameterizedType mapType = (ParameterizedType) field.getGenericType();
                        Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];

                        if (valueType.isAnnotationPresent(ConfigSerializable.class)) {
                            Map<String, Object> serializedMap = new HashMap<>();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) defaultValue;

                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                if (entry.getValue() != null) {
                                    serializedMap.put(entry.getKey(), ConfigSerializer.serialize(entry.getValue()));
                                } else {
                                    serializedMap.put(entry.getKey(), null);
                                }
                            }
                            valueToSave = serializedMap;
                        }
                    }
                    // Handle single ConfigSerializable objects
                    else if (defaultValue.getClass().isAnnotationPresent(ConfigSerializable.class)) {
                        valueToSave = ConfigSerializer.serialize(defaultValue);
                    }

                    yaml.set(path, valueToSave);
                    field.set(instance, defaultValue);

                    // Add comment
                    Comment comment = field.getAnnotation(Comment.class);
                    if (comment != null && comment.above()) {
                        yaml.setComments(path, Arrays.asList(comment.value().split("\n")));
                    }
                }
            }
        }
    }

    /**
     * Processes fields during loading.
     */
    private void processFields(Object instance, YamlConfiguration yaml, Class<?> clazz) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, "");

            // Handle @ConfigSection
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                if (yaml.isConfigurationSection(path)) {
                    Object sectionInstance = field.get(instance);
                    if (sectionInstance == null) {
                        sectionInstance = field.getType().getDeclaredConstructor().newInstance();
                        field.set(instance, sectionInstance);
                    }
                    processFields(sectionInstance, yaml, field.getType());
                }
                continue;
            }

            // Handle @ConfigValue
            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue != null) {
                loadFieldValue(instance, field, yaml, path);
            }
        }
    }

    /**
     * Loads a single field value.
     */
    @SuppressWarnings("unchecked")
    private void loadFieldValue(Object instance, Field field, YamlConfiguration yaml, String path) throws Exception {
        if (!yaml.contains(path)) {
            // Use default value
            Object defaultValue = getDefaultValue(field);
            if (defaultValue != null) {
                field.set(instance, defaultValue);
            } else if (field.getAnnotation(ConfigValue.class).required()) {
                throw new IllegalStateException(InternalMessages.ERR_REQUIRED_CONFIG_MISSING.get("path", path));
            }
            return;
        }

        Object value = yaml.get(path);

        // Handle complex types
        if (value != null) {
            Class<?> fieldType = field.getType();

            // Handle lists
            if (List.class.isAssignableFrom(fieldType) && value instanceof List) {
                ParameterizedType listType = (ParameterizedType) field.getGenericType();
                Class<?> elementType = (Class<?>) listType.getActualTypeArguments()[0];

                List<?> list = (List<?>) value;

                // Check if elements need deserialization
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

                // Process list if processor is present
                ListProcessor processor = field.getAnnotation(ListProcessor.class);
                if (processor != null) {
                    value = processList(field, (List<?>) value);
                }
            }
            // Handle maps
            else if (Map.class.isAssignableFrom(fieldType)) {
                // Handle ConfigurationSection (Bukkit's way of representing YAML sections)
                if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                    org.bukkit.configuration.ConfigurationSection section = (org.bukkit.configuration.ConfigurationSection) value;
                    Map<String, Object> map = new HashMap<>();

                    for (String key : section.getKeys(false)) {
                        map.put(key, section.get(key));
                    }
                    value = map;
                }

                if (value instanceof Map) {
                    ParameterizedType mapType = (ParameterizedType) field.getGenericType();
                    Class<?> valueType = (Class<?>) mapType.getActualTypeArguments()[1];

                    Map<String, Object> map = (Map<String, Object>) value;

                    // Check if values need deserialization
                    if (valueType.isAnnotationPresent(ConfigSerializable.class)) {
                        Map<String, Object> deserializedMap = new HashMap<>();
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            if (entry.getValue() instanceof Map) {
                                deserializedMap.put(entry.getKey(),
                                        ConfigSerializer.deserialize((Map<String, Object>) entry.getValue(),
                                                valueType));
                            } else if (entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) {
                                // Convert ConfigurationSection to Map for deserialization
                                org.bukkit.configuration.ConfigurationSection subSection = (org.bukkit.configuration.ConfigurationSection) entry
                                        .getValue();
                                Map<String, Object> subMap = new HashMap<>();
                                for (String subKey : subSection.getKeys(false)) {
                                    subMap.put(subKey, subSection.get(subKey));
                                }
                                deserializedMap.put(entry.getKey(),
                                        ConfigSerializer.deserialize(subMap, valueType));
                            } else {
                                deserializedMap.put(entry.getKey(), entry.getValue());
                            }
                        }
                        value = deserializedMap;
                    }
                }
            }
            // Handle single ConfigSerializable objects
            else if (fieldType.isAnnotationPresent(ConfigSerializable.class) && value instanceof Map) {
                value = ConfigSerializer.deserialize((Map<String, Object>) value, fieldType);
            }
        }

        field.set(instance, value);
    }

    /**
     * Processes a list value.
     */
    @SuppressWarnings("unchecked")
    private List<?> processList(Field field, List<?> list) throws Exception {
        ListProcessor processor = field.getAnnotation(ListProcessor.class);
        if (processor == null)
            return list;

        @SuppressWarnings("rawtypes")
        ListItemProcessor itemProcessor = processor.value().getDeclaredConstructor().newInstance();
        List<Object> result = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            @SuppressWarnings("rawtypes")
            ListItemProcessor.ProcessResult processResult = itemProcessor.process(item, i);

            if (processResult.shouldUseDefault()) {
                // Get default value for this index
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

    /**
     * Gets default value for a field.
     */
    private Object getDefaultValue(Field field) throws Exception {
        DefaultValue defaultAnnotation = field.getAnnotation(DefaultValue.class);
        if (defaultAnnotation == null)
            return null;

        // Use provider if specified
        if (defaultAnnotation.provider() != NoDefaultValueProvider.class) {
            DefaultValueProvider<?> provider = defaultAnnotation.provider().getDeclaredConstructor().newInstance();
            return provider.provide();
        }

        // Parse string value
        String stringValue = defaultAnnotation.value();
        if (stringValue.isEmpty())
            return null;

        return parseValue(stringValue, field.getType());
    }

    /**
     * Parses string value to target type.
     */
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

        // Add more type conversions as needed
        return value;
    }

    /**
     * Checks if field is a config field.
     */
    private boolean isConfigField(Field field) {
        return field.isAnnotationPresent(ConfigValue.class) ||
                field.isAnnotationPresent(ConfigSection.class);
    }

    /**
     * Gets the configuration path for a field.
     */
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
     * Saves configuration to file.
     * 
     * @param <T>         the configuration type
     * @param configClass the configuration class
     */
    public <T> void save(Class<T> configClass) {
        try {
            T instance = getConfig(configClass);
            if (instance == null)
                return;

            ConfigMetadata metadata = configMetadata.get(configClass);
            File configFile = new File(plugin.getDataFolder(), metadata.getFilePath());

            YamlConfiguration yaml = new YamlConfiguration();
            saveFields(instance, yaml, instance.getClass(), "");

            yaml.save(configFile);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save config: " + configClass.getName(), e);
        }
    }

    /**
     * Saves fields to YAML.
     */
    @SuppressWarnings("unchecked")
    private void saveFields(Object instance, YamlConfiguration yaml, Class<?> clazz, String prefix) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);

            if (!isConfigField(field))
                continue;

            String path = getFieldPath(field, prefix);
            Object value = field.get(instance);

            // If value is null but field has default value, use the default
            if (value == null) {
                DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
                if (defaultValue != null) {
                    value = getDefaultValue(field);
                    if (value != null) {
                        field.set(instance, value);
                    }
                }

                // If still null, skip this field
                if (value == null)
                    continue;
            }

            // Handle @SaveTo
            SaveTo saveTo = field.getAnnotation(SaveTo.class);
            if (saveTo != null) {
                // Save to different file - implement later
                continue;
            }

            // Handle sections
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null) {
                saveFields(value, yaml, value.getClass(), path + ".");
                continue;
            }

            // Handle complex types before saving
            Class<?> fieldType = field.getType();

            // Handle lists with ConfigSerializable elements
            if (value instanceof List && fieldType.equals(List.class)) {
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0).getClass().isAnnotationPresent(ConfigSerializable.class)) {
                    List<Map<String, Object>> serializedList = new ArrayList<>();
                    for (Object item : list) {
                        serializedList.add(ConfigSerializer.serialize(item));
                    }
                    value = serializedList;
                }
            }
            // Handle maps with ConfigSerializable values
            else if (value instanceof Map && fieldType.equals(Map.class)) {
                Map<String, Object> map = (Map<String, Object>) value;
                Map<String, Object> serializedMap = new HashMap<>();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getValue() != null &&
                            entry.getValue().getClass().isAnnotationPresent(ConfigSerializable.class)) {
                        serializedMap.put(entry.getKey(), ConfigSerializer.serialize(entry.getValue()));
                    } else {
                        serializedMap.put(entry.getKey(), entry.getValue());
                    }
                }
                value = serializedMap;
            }
            // Handle single ConfigSerializable objects
            else if (value.getClass().isAnnotationPresent(ConfigSerializable.class)) {
                value = ConfigSerializer.serialize(value);
            }

            // Add comment if present
            Comment comment = field.getAnnotation(Comment.class);
            if (comment != null && comment.above()) {
                yaml.setComments(path, Arrays.asList(comment.value().split("\n")));
            }

            yaml.set(path, value);
        }
    }

    /**
     * Gets a registered configuration.
     * 
     * @param <T>         the configuration type
     * @param configClass the configuration class
     * @return the configuration instance or null if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<T> configClass) {
        return (T) registeredConfigs.get(configClass);
    }

    /**
     * Reloads a configuration.
     * 
     * @param <T>         the configuration type
     * @param configClass the configuration class to reload
     */
    public <T> void reload(Class<T> configClass) {
        reload(configClass, new String[0]);
    }

    /**
     * Reloads specific sections of a configuration.
     * 
     * @param <T>         the configuration type
     * @param configClass the configuration class
     * @param sections    the sections to reload
     */
    public <T> void reload(Class<T> configClass, String... sections) {
        try {
            T instance = getConfig(configClass);
            if (instance == null)
                return;

            ConfigMetadata metadata = configMetadata.get(configClass);

            // Check if sections are reloadable
            ConfigReloadable reloadable = configClass.getAnnotation(ConfigReloadable.class);
            if (reloadable != null && reloadable.sections().length > 0) {
                Set<String> allowedSections = new HashSet<>(Arrays.asList(reloadable.sections()));
                for (String section : sections) {
                    if (!allowedSections.contains(section)) {
                        plugin.getLogger().warning(InternalMessages.SYS_SECTION_NOT_RELOADABLE.get("section", section));
                        return;
                    }
                }
            }

            // Reload
            loadConfig(instance, metadata);

            // Notify listeners
            if (reloadable != null && reloadable.notifyOnChange()) {
                notifyChangeListeners(configClass, new HashSet<>(Arrays.asList(sections)));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to reload config: " + configClass.getName(), e);
        }
    }

    /**
     * Registers a change listener.
     * 
     * @param <T>         the configuration type
     * @param configClass the configuration class
     * @param listener    the listener to notify on changes
     */
    @SuppressWarnings("unchecked")
    public <T> void onChange(Class<T> configClass, BiConsumer<T, Set<String>> listener) {
        changeListeners.computeIfAbsent(configClass, k -> new ArrayList<>())
                .add((BiConsumer<Object, Set<String>>) listener);
    }

    /**
     * Notifies change listeners.
     */
    private void notifyChangeListeners(Class<?> configClass, Set<String> changedSections) {
        List<BiConsumer<Object, Set<String>>> listeners = changeListeners.get(configClass);
        if (listeners == null)
            return;

        Object config = getConfig(configClass);
        for (BiConsumer<Object, Set<String>> listener : listeners) {
            listener.accept(config, changedSections);
        }
    }

    /**
     * Internal metadata storage.
     */
    private static class ConfigMetadata {
        @Getter
        private final String filePath;
        @Getter
        private final boolean autoCreate;
        @Getter
        private final String templatePath;

        ConfigMetadata(ConfigFile annotation, Map<String, String> placeholders) {
            String path = annotation.value();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            this.filePath = path;
            this.autoCreate = annotation.autoCreate();
            this.templatePath = annotation.template();
        }
    }
}