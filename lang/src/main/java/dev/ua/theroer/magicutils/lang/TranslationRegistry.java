package dev.ua.theroer.magicutils.lang;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translations a {@link LanguageManager} knows about besides the files on disk: the
 * values plugins register at runtime and the bundled {@code lang/<code>.json} defaults.
 * Merges both into a loaded {@link LanguageConfig} without overriding user edits, and
 * answers lookups for keys the loaded file does not carry.
 */
final class TranslationRegistry {
    private final Map<String, Map<String, String>> registered = new ConcurrentHashMap<>();

    /**
     * Merges {@code translations} into the registered set for {@code languageCode}.
     *
     * @return the normalized language code, or {@code null} when nothing was registered
     */
    String register(String languageCode, Map<String, String> translations) {
        if (languageCode == null || languageCode.isBlank() || translations == null || translations.isEmpty()) {
            return null;
        }
        String normalizedLanguage = languageCode.trim();
        Map<String, String> normalizedTranslations = normalize(translations);
        if (normalizedTranslations.isEmpty()) {
            return null;
        }
        registered.compute(normalizedLanguage, (ignored, existing) -> {
            Map<String, String> merged = new LinkedHashMap<>();
            if (existing != null && !existing.isEmpty()) {
                merged.putAll(existing);
            }
            merged.putAll(normalizedTranslations);
            return merged;
        });
        return normalizedLanguage;
    }

    boolean hasBundled(String languageCode) {
        return !BundledTranslations.getSections(languageCode).isEmpty();
    }

    /**
     * Seeds a just-loaded language file with the bundled defaults for its code.
     *
     * @return true when the config changed and should be saved
     */
    boolean initializeDefaults(LanguageConfig config, String languageCode, boolean existed) {
        Map<String, Map<String, String>> translations = BundledTranslations.getSections(languageCode);
        boolean changed = false;
        if (!existed) {
            // Stamp the real code even when nothing is bundled for this language:
            // LanguageMetadata defaults to English/en, so a fresh ru_ru.jsonc would
            // otherwise claim to be the English file.
            changed = config.applyLanguageCode(languageCode);
        }
        if (translations.isEmpty()) {
            return changed;
        }
        // Fill defaults even into a file that already exists. lang/<code> is shared
        // by every MagicUtils-based mod on the server, so whichever mod boots first
        // creates it and the rest used to skip their own defaults entirely, leaving
        // their keys unresolved. override=false keeps user edits and the first
        // writer's values intact. Only report a change when something was actually
        // filled in, so an up-to-date file is not rewritten on every start.
        return config.applyTranslations(translations, !existed) | changed;
    }

    /**
     * Copies registered translations into {@code config}'s custom messages, keeping any
     * value already present there (user edits win).
     *
     * @return true when at least one entry was added
     */
    boolean applyTo(LanguageConfig config, String languageCode) {
        if (config == null || languageCode == null || languageCode.isBlank()) {
            return false;
        }
        Map<String, String> translations = registered.get(languageCode);
        if (translations == null || translations.isEmpty()) {
            return false;
        }

        boolean changed = false;
        Map<String, String> customMessages = config.getCustomMessages();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || customMessages.containsKey(key)) {
                continue;
            }
            customMessages.put(key, value);
            changed = true;
        }
        return changed;
    }

    /**
     * Resolves {@code key} from registered translations, then from the bundled sections.
     *
     * @return the message, or {@code null} when neither source has it
     */
    String lookup(String languageCode, String key) {
        if (languageCode == null || languageCode.isBlank() || key == null || key.isBlank()) {
            return null;
        }

        Map<String, String> registeredForLanguage = registered.get(languageCode);
        if (registeredForLanguage != null) {
            String registeredMessage = registeredForLanguage.get(key);
            if (registeredMessage != null) {
                return registeredMessage;
            }
        }

        Map<String, Map<String, String>> bundled = BundledTranslations.getSections(languageCode);
        if (bundled.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Map<String, String>> sectionEntry : bundled.entrySet()) {
            if (sectionEntry == null || sectionEntry.getKey() == null || sectionEntry.getValue() == null) {
                continue;
            }
            String section = sectionEntry.getKey();
            if (!key.startsWith(section + ".")) {
                continue;
            }
            String relativeKey = key.substring(section.length() + 1);
            String bundledMessage = sectionEntry.getValue().get(relativeKey);
            if (bundledMessage != null) {
                return bundledMessage;
            }
        }
        return null;
    }

    private static Map<String, String> normalize(Map<String, String> translations) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }
}
