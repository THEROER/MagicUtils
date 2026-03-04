package dev.ua.theroer.magicutils.platform;

import java.util.UUID;

/**
 * Normalized player-originated message emitted by the platform layer.
 *
 * @param playerId stable player id when available
 * @param playerName current player display/login name
 * @param message raw chat content or raw command line
 * @param type source message type
 */
public record PlayerMessage(UUID playerId, String playerName, String message, PlayerMessageType type) {
    /**
     * Returns true when the payload contains enough data to process safely.
     *
     * @return true for non-empty player name and message
     */
    public boolean isValid() {
        return playerName != null
                && !playerName.isBlank()
                && message != null
                && !message.isBlank()
                && type != null;
    }
}
