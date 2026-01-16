package dev.ua.theroer.magicutils.platform;

/**
 * Optional platform hook for lifecycle shutdown events.
 */
public interface ShutdownHookRegistrar {
    /**
     * Registers a shutdown hook that fires when the platform is stopping.
     *
     * @param hook shutdown callback
     */
    void registerShutdownHook(Runnable hook);

    /**
     * Unregisters a previously registered shutdown hook.
     *
     * @param hook shutdown callback to remove
     */
    default void unregisterShutdownHook(Runnable hook) {
        // Optional
    }
}
