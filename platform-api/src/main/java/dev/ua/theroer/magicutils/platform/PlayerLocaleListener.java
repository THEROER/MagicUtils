package dev.ua.theroer.magicutils.platform;

/**
 * Listener for normalized player locale updates.
 */
@FunctionalInterface
public interface PlayerLocaleListener {
    /**
     * Called when the platform observes a player locale update.
     *
     * @param playerLocale normalized player locale update
     */
    void onPlayerLocale(PlayerLocale playerLocale);
}
