package dev.ua.theroer.magicutils.platform;

/**
 * Listener for normalized player join/leave events.
 */
@FunctionalInterface
public interface PlayerLifecycleListener {
    /**
     * Called when the platform observes a player lifecycle transition.
     *
     * @param lifecycle normalized player lifecycle event
     */
    void onPlayerLifecycle(PlayerLifecycle lifecycle);
}
