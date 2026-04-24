package dev.ua.theroer.magicutils.platform;

import java.util.UUID;

/**
 * Normalized player locale update emitted by the platform layer.
 *
 * @param playerId stable player id when available
 * @param playerName current player display/login name
 * @param localeTag client locale tag reported by the platform
 */
public record PlayerLocale(UUID playerId, String playerName, String localeTag) {
    /**
     * Returns true when the payload contains enough data to process safely.
     *
     * @return true for non-empty player name and locale tag
     */
    public boolean isValid() {
        return playerName != null
                && !playerName.isBlank()
                && localeTag != null
                && !localeTag.isBlank();
    }
}
