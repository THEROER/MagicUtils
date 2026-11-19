package dev.ua.theroer.magicutils.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Supported on-disk config formats and their (lazily created) Jackson mappers.
 */
enum ConfigFormat {
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
        ConfigDocument.ensureParentDirectory(file);
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

    static ConfigFormat defaultFormat() {
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
