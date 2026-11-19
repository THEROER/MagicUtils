package dev.ua.theroer.magicutils.lang;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Discovers language files under {@code <configDir>/lang}, in any of the config formats
 * MagicUtils can read.
 */
final class LanguageFiles {
    static final Set<String> EXTENSIONS = Set.of("jsonc", "json", "yml", "yaml", "toml");

    private LanguageFiles() {
    }

    /**
     * Lists the language codes (file names without extension) present on disk.
     */
    static Set<String> codesOnDisk(Path configDir) {
        Set<String> languages = new HashSet<>();
        File langDir = configDir.resolve("lang").toFile();
        if (!langDir.exists() || !langDir.isDirectory()) {
            return languages;
        }
        File[] files = langDir.listFiles((dir, name) -> {
            if (name == null) {
                return false;
            }
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || dot >= name.length() - 1) {
                return false;
            }
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            return EXTENSIONS.contains(ext);
        });
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    languages.add(name.substring(0, dot));
                }
            }
        }
        return languages;
    }

    static boolean exists(Path configDir, String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        Path base = configDir.resolve("lang");
        for (String ext : EXTENSIONS) {
            Path candidate = base.resolve(languageCode + "." + ext);
            if (candidate.toFile().exists()) {
                return true;
            }
        }
        return false;
    }
}
