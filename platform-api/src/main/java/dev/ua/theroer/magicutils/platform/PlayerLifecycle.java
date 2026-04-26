package dev.ua.theroer.magicutils.platform;

import java.util.UUID;

/**
 * Normalized player join/leave event emitted by the platform layer.
 *
 * @param playerId stable player id when available
 * @param playerName current player display/login name
 * @param type lifecycle transition type
 */
public record PlayerLifecycle(UUID playerId, String playerName, PlayerLifecycleType type) {
    /**
     * Returns true when the payload contains enough data to process safely.
     *
     * @return true for non-empty player name and non-null lifecycle type
     */
    public boolean isValid() {
        return playerName != null
                && !playerName.isBlank()
                && type != null;
    }
}
