package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the concrete file path/format of a {@link ConfigFile} template: placeholder
 * and {@code {ext}} substitution, per-file/global format preferences, and detection
 * of existing files in other formats that need migrating.
 */
final class ConfigFormatResolver {
    private static final String EXT_TOKEN = "{ext}";
    private static final String GLOBAL_FORMAT_FILE = "magicutils.format";
    private static final String GLOBAL_FORMAT_PROPERTY = "magicutils.config.format";
    private static final String GLOBAL_FORMAT_ENV = "MAGICUTILS_CONFIG_FORMAT";
    private static final String DEFAULT_EXTENSION = "yml";
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("jsonc", "json", "yml", "yaml", "toml");

    private final Platform platform;
    private final PlatformLogger logger;

    ConfigFormatResolver(Platform platform, PlatformLogger logger) {
        this.platform = platform;
        this.logger = logger;
    }

    static Map<String, String> toPlaceholderMap(String... placeholders) {
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

    private static String applyPlaceholders(String path, Map<String, String> placeholders) {
        if (path == null || placeholders == null || placeholders.isEmpty()) {
            return path;
        }
        String resolved = path;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    FormatDecision resolveFormatDecision(ConfigFile configFile, Map<String, String> placeholders) {
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

    private record ExtensionInfo(String basePath, String extension) {
    }

    record FormatDecision(String targetPath, String sourcePath,
                                  String targetExt, String sourceExt,
                                  boolean formatAware) {
        static FormatDecision fixed(String targetPath) {
            return new FormatDecision(targetPath, null, null, null, false);
        }
    }
}
