package dev.ua.theroer.magicutils.config;

import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sanitized (config-dir-relative, traversal-checked) location and options of a config file.
 */
final class ConfigMetadata {
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

    File resolveFile(Path baseDir) {
        return baseDir.resolve(filePath).toFile();
    }
}
