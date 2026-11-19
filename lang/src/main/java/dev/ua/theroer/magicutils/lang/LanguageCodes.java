package dev.ua.theroer.magicutils.lang;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure helpers for matching client locale tags (e.g. {@code en_US}, {@code uk-UA}) to
 * language codes.
 */
final class LanguageCodes {

    private LanguageCodes() {
    }

    /**
     * Lookup candidates for a tag, most specific first: as given, lower-cased,
     * hyphen-normalized, then the bare language part.
     */
    static Set<String> candidates(String languageCode) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String trimmed = languageCode != null ? languageCode.trim() : "";
        if (trimmed.isEmpty()) {
            return candidates;
        }
        candidates.add(trimmed);

        String lowerCased = trimmed.toLowerCase(Locale.ROOT);
        candidates.add(lowerCased);

        String normalizedTag = normalizeKey(trimmed);
        candidates.add(normalizedTag);

        int hyphenIndex = normalizedTag.indexOf('-');
        if (hyphenIndex > 0) {
            candidates.add(normalizedTag.substring(0, hyphenIndex));
        }

        int underscoreIndex = lowerCased.indexOf('_');
        if (underscoreIndex > 0) {
            candidates.add(lowerCased.substring(0, underscoreIndex));
        }

        return candidates;
    }

    /** Lower-cases a code and turns {@code _} into {@code -} for comparison. */
    static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
