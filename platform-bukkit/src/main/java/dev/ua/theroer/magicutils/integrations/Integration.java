package dev.ua.theroer.magicutils.integrations;

import org.bukkit.plugin.java.JavaPlugin;

import dev.ua.theroer.magicutils.Logger;
import lombok.Getter;

/**
 * Abstract class for all integrations with external plugins
 * Provides basic logic for availability checking and initialization
 * 
 * @param <T> type of integration result (e.g., PlaceholderEngine for
 *            PlaceholderAPI)
 */
public abstract class Integration<T> {

    /** The plugin instance that creates this integration */
    protected final JavaPlugin plugin;
    /** The name of the target plugin for integration */
    @Getter
    protected final String targetPlugin;
    /** The integration implementation instance */
    protected T implementation;
    /** Whether this integration has been initialized */
    protected boolean initialized = false;
    /** Whether the target plugin is available and integration is working */
    protected boolean available = false;

    /**
     * Creates a new integration
     * 
     * @param plugin       plugin instance
     * @param targetPlugin name of the target plugin for integration
     */
    protected Integration(JavaPlugin plugin, String targetPlugin) {
        this.plugin = plugin;
        this.targetPlugin = targetPlugin;
    }

    /**
     * Checks if the target plugin is available
     * 
     * @return true if plugin is available
     */
    public boolean isAvailable() {
        if (!initialized) {
            initialize();
        }
        return available;
    }

    /**
     * Gets the integration implementation
     * 
     * @return integration implementation or fallback if plugin is unavailable
     */
    public T getImplementation() {
        if (!initialized) {
            initialize();
        }
        return implementation;
    }

    /**
     * Initializes the integration
     */
    private void initialize() {
        if (initialized) {
            return;
        }

        available = plugin.getServer().getPluginManager().isPluginEnabled(targetPlugin);

        if (available) {
            try {
                implementation = createImplementation();
                onIntegrationEnabled();
                Logger.info().toConsole().send(targetPlugin + " detected: integration enabled.");
            } catch (Throwable e) {
                available = false;
                implementation = createFallback();
                onIntegrationFailed(e);
                Logger.warn().toConsole().send("Failed to initialize integration with " + targetPlugin
                        + ", using fallback. " + e.getMessage());
            }
        } else {
            implementation = createFallback();
            onIntegrationDisabled();
            Logger.info().toConsole().send(targetPlugin + " not found: using fallback.");
        }

        initialized = true;
    }

    /**
     * Creates the real integration implementation
     * Called only if the target plugin is available
     * 
     * @return integration implementation
     * @throws Exception if failed to create implementation
     */
    protected abstract T createImplementation() throws Exception;

    /**
     * Creates fallback implementation
     * Called if target plugin is unavailable or initialization failed
     * 
     * @return fallback implementation
     */
    protected abstract T createFallback();

    /**
     * Called when integration is successfully enabled
     */
    protected void onIntegrationEnabled() {
        // Does nothing by default
    }

    /**
     * Called when integration failed
     * 
     * @param error the error that occurred
     */
    protected void onIntegrationFailed(Throwable error) {
        // Does nothing by default
    }

    /**
     * Called when target plugin is unavailable
     */
    protected void onIntegrationDisabled() {
        // Does nothing by default
    }
}