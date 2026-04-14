package dev.ua.theroer.magicutils.platform;

/**
 * Handle for unregistering a previously subscribed listener.
 */
@FunctionalInterface
public interface ListenerSubscription extends AutoCloseable {
    /**
     * A no-op listener subscription that does nothing when closed.
     */
    ListenerSubscription NOOP = () -> {
    };

    /**
     * Unregisters the listener.
     */
    @Override
    void close();

    /**
     * Returns a no-op subscription for unsupported platforms/capabilities.
     *
     * @return no-op subscription
     */
    static ListenerSubscription noop() {
        return NOOP;
    }
}
