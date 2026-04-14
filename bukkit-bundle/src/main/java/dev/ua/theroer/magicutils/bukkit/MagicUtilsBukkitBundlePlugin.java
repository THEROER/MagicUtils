package dev.ua.theroer.magicutils.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared MagicUtils runtime plugin for Bukkit/Paper servers.
 */
public final class MagicUtilsBukkitBundlePlugin extends JavaPlugin {
    /**
     * Creates a new instance of the MagicUtils bundle plugin.
     * Required by the Bukkit platform to initialize the plugin.
     */
    public MagicUtilsBukkitBundlePlugin() {
    }

    @Override
    public void onEnable() {
        getLogger().info("MagicUtils Bukkit bundle loaded.");
    }
}
