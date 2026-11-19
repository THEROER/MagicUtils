package dev.ua.theroer.magicutils.lang;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player language preferences held by a {@link LanguageManager}: an explicit choice
 * (set by a command or plugin) and an auto-detected one (from the client locale). The
 * explicit choice always wins over the detected one.
 */
final class PlayerLanguageStore {
    private final Map<UUID, String> explicit = new ConcurrentHashMap<>();
    private final Map<UUID, String> autoDetected = new ConcurrentHashMap<>();

    void put(boolean detected, UUID playerId, String languageCode) {
        store(detected).put(playerId, languageCode);
    }

    void remove(boolean detected, UUID playerId) {
        if (playerId != null) {
            store(detected).remove(playerId);
        }
    }

    /**
     * Returns the player's explicit language, else the auto-detected one, else
     * {@code defaultLanguage}.
     */
    String resolve(UUID playerId, String defaultLanguage) {
        if (playerId == null) {
            return defaultLanguage;
        }
        String chosen = explicit.get(playerId);
        if (chosen != null && !chosen.isBlank()) {
            return chosen;
        }
        String detected = autoDetected.get(playerId);
        if (detected != null && !detected.isBlank()) {
            return detected;
        }
        return defaultLanguage;
    }

    Map<UUID, String> snapshot(boolean detected) {
        return Collections.unmodifiableMap(new HashMap<>(store(detected)));
    }

    private Map<UUID, String> store(boolean detected) {
        return detected ? autoDetected : explicit;
    }
}
