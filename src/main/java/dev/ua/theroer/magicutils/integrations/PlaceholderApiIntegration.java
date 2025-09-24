package dev.ua.theroer.magicutils.integrations;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI integration using the new Integration system
 * 
 * Usage:
 * 
 * <pre>
 * PlaceholderApiIntegration papi = new PlaceholderApiIntegration(yourPlugin);
 * String result = papi.renderPlaceholders(player, "Hello %player_name%!");
 * </pre>
 */
public class PlaceholderApiIntegration extends Integration<PlaceholderEngine> {

    /**
     * Creates a new PlaceholderAPI integration
     * 
     * @param plugin your plugin instance
     */
    public PlaceholderApiIntegration(JavaPlugin plugin) {
        super(plugin, "PlaceholderAPI");
    }

    @Override
    protected PlaceholderEngine createImplementation() throws Exception {
        // Load class by name so JVM doesn't try to resolve PAPI early
        Class<?> papiEngineClass = Class.forName("dev.ua.theroer.magicutils.integrations.PapiEngine");
        return (PlaceholderEngine) papiEngineClass.getDeclaredConstructor().newInstance();
    }

    @Override
    protected PlaceholderEngine createFallback() {
        return new NoopEngine();
    }

    /**
     * Processes placeholders in text
     * 
     * @param player player for placeholder context (can be null)
     * @param text   text with placeholders
     * @return text with processed placeholders
     */
    public String renderPlaceholders(@Nullable Player player, String text) {
        return getImplementation().apply(player, text);
    }

    /**
     * Static method for quick placeholder processing
     * Automatically creates integration and processes text
     * 
     * @param plugin plugin instance
     * @param player player for placeholder context (can be null)
     * @param text   text with placeholders
     * @return text with processed placeholders
     */
    public static String process(JavaPlugin plugin, @Nullable Player player, String text) {
        PlaceholderApiIntegration integration = new PlaceholderApiIntegration(plugin);
        return integration.renderPlaceholders(player, text);
    }

    /**
     * Provides access to the underlying PlaceholderEngine without re-instantiation.
     * 
     * @return the PlaceholderEngine implementation (real or fallback)
     */
    public PlaceholderEngine getImplementationLazy() {
        return getImplementation();
    }
}