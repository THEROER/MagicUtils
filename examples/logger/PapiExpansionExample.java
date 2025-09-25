package examples.logger;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Example PlaceholderAPI expansion that works alongside PlaceholderProcessor.
 * Register this in your plugin's onEnable() method.
 * 
 * Usage:
 * - %yourplugin_example% -> "Hello World"
 * - %yourplugin_player_name% -> player's display name
 * 
 * Combined with PlaceholderProcessor custom placeholders:
 * Logger.info().to(player).send("PAPI: %yourplugin_example%, Custom: {server-online}");
 */
public class PapiExpansionExample extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    public PapiExpansionExample(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yourplugin"; // Change this to your plugin name
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getPluginMeta().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep expansion loaded even if PlaceholderAPI reloads
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        switch (params.toLowerCase()) {
            case "example":
                return "Hello World";
                
            case "player_name":
                return player != null ? player.displayName() : "Console";
                
            case "plugin_version":
                return plugin.getPluginMeta().getVersion();
                
            default:
                return null; // Placeholder not found
        }
    }

    /**
     * Register this expansion in your plugin's onEnable() method:
     * 
     * if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
     *     new PapiExpansionExample(this).register();
     * }
     */
}
