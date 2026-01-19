package dev.ua.theroer.magicutils.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared MagicUtils runtime plugin for Bukkit/Paper servers.
 */
public final class MagicUtilsBukkitBundlePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("MagicUtils Bukkit bundle loaded.");
    }
}
