package dev.ua.theroer.magicutils.platform;

/**
 * Listener for normalized player-originated messages.
 */
@FunctionalInterface
public interface PlayerMessageListener {
    /**
     * Called when the platform observes a player chat message or command.
     *
     * @param message normalized player message
     */
    void onPlayerMessage(PlayerMessage message);
}
