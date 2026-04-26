package dev.ua.theroer.magicutils.lang;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Loads bundled MagicUtils translations from {@code /lang/<code>.json}
 * classpath resources.
 *
 * <p>Files use flat dot-delimited keys and support JSON with comments
 * (including trailing commas). Keys starting with {@code _} are treated
 * as translator-facing comments and dropped at load time.</p>
 *
 * <p>Parsed resources are cached per language code since classpath
 * resources are immutable during a JVM lifetime.</p>
 */
public final class BundledTranslations {

    private static final String RESOURCE_PREFIX = "lang/";
    private static final String RESOURCE_SUFFIX = ".json";
    private static final String COMMENT_PREFIX = "_";

    private static final ObjectMapper MAPPER = JsonMapper.builder(
                    JsonFactory.builder()
                            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                            .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                            .build())
            .build();

    private static final TypeReference<LinkedHashMap<String, String>> FLAT_MAP_TYPE =
            new TypeReference<>() {};

    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Map<String, String>>> SECTION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> NAMESPACE_CACHE = new ConcurrentHashMap<>();

    private BundledTranslations() {
    }

    /**
     * Loads translations from {@code /lang/<namespace>/<code>.json}
     * inside the calling plugin's classpath. Useful for plugins that
     * want to ship their own bundled translations alongside the
     * MagicUtils core ones without polluting {@code /lang/<code>.json}.
     *
     * @param namespace plugin namespace (e.g. {@code "leavepulse"})
     * @param languageCode language code
     * @return flat translation map, or empty when not found
     */
    public static Map<String, String> getNamespacedTranslations(String namespace, String languageCode) {
        if (namespace == null || namespace.isBlank() || languageCode == null || languageCode.isBlank()) {
            return Collections.emptyMap();
        }
        String cacheKey = namespace.trim().toLowerCase(Locale.ROOT) + "/" + languageCode;
        return NAMESPACE_CACHE.computeIfAbsent(cacheKey,
                ignored -> loadNamespacedResource(namespace, languageCode));
    }

    /**
     * Scans the classpath under {@code /lang/<namespace>/} and returns
     * every available language code.
     *
     * @param namespace plugin namespace
     * @return language codes available for that namespace
     */
    public static Set<String> availableNamespacedCodes(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> codes = new LinkedHashSet<>();
        String prefix = RESOURCE_PREFIX + namespace.trim().toLowerCase(Locale.ROOT) + "/";
        for (ClassLoader loader : loaders()) {
            try {
                Enumeration<URL> resources = loader.getResources(prefix);
                while (resources.hasMoreElements()) {
                    collectCodes(resources.nextElement(), codes);
                }
            } catch (IOException ignored) {
            }
        }
        return codes;
    }

    private static Map<String, String> loadNamespacedResource(String namespace, String languageCode) {
        String path = RESOURCE_PREFIX + namespace.trim().toLowerCase(Locale.ROOT) + "/" + languageCode + RESOURCE_SUFFIX;
        URL url = findResourceAt(path);
        if (url == null) {
            return Collections.emptyMap();
        }
        return parseFlat(url, namespace + "/" + languageCode);
    }

    /**
     * Returns the flat {@code key -> value} translation map for the given
     * language code, loading from classpath on first access.
     *
     * @param languageCode language code (e.g. {@code "en"})
     * @return unmodifiable flat translations, or an empty map when no
     *         bundled resource exists
     */
    public static Map<String, String> getTranslations(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return Collections.emptyMap();
        }
        return CACHE.computeIfAbsent(languageCode, BundledTranslations::loadResource);
    }

    /**
     * Returns the translations grouped by section (the part before the
     * first dot in each key). Compatible with
     * {@code LanguageConfig.applyTranslations()}.
     *
     * <p>For example, the flat key {@code "magicutils.commands.no_permission"}
     * is stored under section {@code "magicutils.commands"} with relative
     * key {@code "no_permission"}.</p>
     *
     * <p>The {@code language} section maps {@code language.name},
     * {@code language.code}, etc. directly since it has no nested
     * structure.</p>
     *
     * @param languageCode language code
     * @return unmodifiable sectioned translations
     */
    public static Map<String, Map<String, String>> getSections(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return Collections.emptyMap();
        }
        return SECTION_CACHE.computeIfAbsent(languageCode, BundledTranslations::buildSections);
    }

    /**
     * @param languageCode language code
     * @return true when a bundled resource exists for this code
     */
    public static boolean exists(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        return findResource(languageCode) != null;
    }

    /**
     * Scans the classpath for bundled {@code lang/*.json} files and
     * returns their language codes. Scans both plain directories (local
     * development) and jars (production).
     *
     * @return language codes available as bundled resources
     */
    public static Set<String> availableCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (ClassLoader loader : loaders()) {
            try {
                Enumeration<URL> resources = loader.getResources(RESOURCE_PREFIX);
                while (resources.hasMoreElements()) {
                    collectCodes(resources.nextElement(), codes);
                }
            } catch (IOException ignored) {
            }
        }
        return codes;
    }

    private static Map<String, String> loadResource(String languageCode) {
        URL url = findResource(languageCode);
        if (url == null) {
            return Collections.emptyMap();
        }
        return parseFlat(url, languageCode);
    }

    private static Map<String, String> parseFlat(URL url, String diagnosticName) {
        try (InputStream in = url.openStream()) {
            if (in == null) {
                return Collections.emptyMap();
            }
            LinkedHashMap<String, String> raw = MAPPER.readValue(in, FLAT_MAP_TYPE);
            LinkedHashMap<String, String> filtered = new LinkedHashMap<>(raw.size());
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isEmpty() || key.startsWith(COMMENT_PREFIX)) {
                    continue;
                }
                filtered.put(key, entry.getValue());
            }
            return Collections.unmodifiableMap(filtered);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to parse bundled translations for: " + diagnosticName, e);
        }
    }

    private static URL findResourceAt(String path) {
        for (ClassLoader loader : loaders()) {
            URL url = loader.getResource(path);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static Map<String, Map<String, String>> buildSections(String languageCode) {
        Map<String, String> flat = getTranslations(languageCode);
        if (flat.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Map<String, String>> sections = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            SectionSplit split = splitKey(key);
            if (split == null) {
                continue;
            }
            sections.computeIfAbsent(split.section, ignored -> new LinkedHashMap<>())
                    .put(split.relative, value);
        }
        LinkedHashMap<String, Map<String, String>> unmodifiable = new LinkedHashMap<>(sections.size());
        for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
            unmodifiable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(unmodifiable);
    }

    private static SectionSplit splitKey(String key) {
        if (key.startsWith("language.")) {
            return new SectionSplit("language", key.substring("language.".length()));
        }
        if (key.startsWith("magicutils.")) {
            int nextDot = key.indexOf('.', "magicutils.".length());
            if (nextDot < 0) {
                return null;
            }
            return new SectionSplit(key.substring(0, nextDot), key.substring(nextDot + 1));
        }
        int firstDot = key.indexOf('.');
        if (firstDot <= 0) {
            return null;
        }
        return new SectionSplit(key.substring(0, firstDot), key.substring(firstDot + 1));
    }

    private static URL findResource(String languageCode) {
        String path = RESOURCE_PREFIX + languageCode + RESOURCE_SUFFIX;
        for (ClassLoader loader : loaders()) {
            URL url = loader.getResource(path);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static List<ClassLoader> loaders() {
        LinkedHashSet<ClassLoader> loaders = new LinkedHashSet<>();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            loaders.add(contextLoader);
        }
        ClassLoader ownLoader = BundledTranslations.class.getClassLoader();
        if (ownLoader != null) {
            loaders.add(ownLoader);
        }
        return List.copyOf(loaders);
    }

    private static void collectCodes(URL url, Set<String> codes) {
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        switch (protocol) {
            case "file" -> collectFromDirectory(url, codes);
            case "jar" -> collectFromJar(url, codes);
            default -> {
            }
        }
    }

    private static void collectFromDirectory(URL url, Set<String> codes) {
        try {
            Path dir = Path.of(url.toURI());
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(RESOURCE_SUFFIX))
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            codes.add(name.substring(0, name.length() - RESOURCE_SUFFIX.length()));
                        });
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectFromJar(URL url, Set<String> codes) {
        try {
            URLConnection connection = url.openConnection();
            if (!(connection instanceof JarURLConnection jarConnection)) {
                return;
            }
            try (JarFile jarFile = jarConnection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(RESOURCE_PREFIX) || !name.endsWith(RESOURCE_SUFFIX)) {
                        continue;
                    }
                    String tail = name.substring(RESOURCE_PREFIX.length());
                    if (tail.contains("/")) {
                        continue;
                    }
                    codes.add(tail.substring(0, tail.length() - RESOURCE_SUFFIX.length()));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private record SectionSplit(String section, String relative) {
    }
}
