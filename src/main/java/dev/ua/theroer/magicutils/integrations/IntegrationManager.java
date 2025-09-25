package dev.ua.theroer.magicutils.integrations;

import org.bukkit.plugin.java.JavaPlugin;

import dev.ua.theroer.magicutils.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Manager for handling and safely loading integrations
 * Provides centralized management of all plugin integrations
 */
public class IntegrationManager {

    private final JavaPlugin plugin;
    private final Map<String, Integration<?>> integrations = new ConcurrentHashMap<>();

    /**
     * Creates a new integration manager
     * 
     * @param plugin plugin instance
     */
    public IntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a new integration
     * 
     * @param <T>         integration type
     * @param key         unique integration key
     * @param integration integration to register
     * @return registered integration
     */
    public <T> Integration<T> register(String key, Integration<T> integration) {
        integrations.put(key, integration);
        Logger.info().toConsole().send("Registered integration: " + key + " -> " + integration.getTargetPlugin());
        return integration;
    }

    /**
     * Registers an integration using a factory method
     * 
     * @param <T>     integration type
     * @param key     unique integration key
     * @param factory factory method to create the integration
     * @return registered integration
     */
    public <T> Integration<T> register(String key, Function<JavaPlugin, Integration<T>> factory) {
        Integration<T> integration = factory.apply(plugin);
        return register(key, integration);
    }

    /**
     * Gets integration by key
     * 
     * @param <T> integration type
     * @param key integration key
     * @return integration or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> Integration<T> getIntegration(String key) {
        return (Integration<T>) integrations.get(key);
    }

    /**
     * Checks if integration is available
     * 
     * @param key integration key
     * @return true if integration is available
     */
    public boolean isAvailable(String key) {
        Integration<?> integration = integrations.get(key);
        return integration != null && integration.isAvailable();
    }

    /**
     * Gets integration implementation
     * 
     * @param <T> implementation type
     * @param key integration key
     * @return integration implementation or null if not found
     */
    public <T> T getImplementation(String key) {
        Integration<T> integration = getIntegration(key);
        return integration != null ? integration.getImplementation() : null;
    }

    /**
     * Initializes all registered integrations
     * Useful for eager loading on plugin startup
     */
    public void initializeAll() {
        Logger.info().toConsole().send("Initializing all integrations...");
        for (Map.Entry<String, Integration<?>> entry : integrations.entrySet()) {
            String key = entry.getKey();
            Integration<?> integration = entry.getValue();
            try {
                integration.isAvailable(); // This will trigger initialization
                Logger.info().toConsole().send("Integration " + key + " initialized: " +
                        (integration.isAvailable() ? "available" : "unavailable"));
            } catch (Exception e) {
                Logger.error().toConsole().send("Error initializing integration " + key + ": " + e.getMessage());
            }
        }
    }

    /**
     * Gets integration statistics
     * 
     * @return statistics string
     */
    public String getStats() {
        int total = integrations.size();
        long available = integrations.values().stream()
                .mapToInt(integration -> integration.isAvailable() ? 1 : 0)
                .sum();

        return String.format("Integrations: %d total, %d available", total, available);
    }

    /**
     * Clears all integrations
     * Useful when disabling the plugin
     */
    public void clear() {
        integrations.clear();
        Logger.info().toConsole().send("All integrations cleared");
    }
}