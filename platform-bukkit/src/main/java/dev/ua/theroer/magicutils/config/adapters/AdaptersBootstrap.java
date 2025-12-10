package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import net.kyori.adventure.text.Component;
import org.bukkit.World;

/**
 * Registers default Bukkit-specific config adapters.
 */
public final class AdaptersBootstrap {
    private AdaptersBootstrap() {
    }

    /**
     * Register built-in adapters for Bukkit types.
     */
    public static void registerDefaults() {
        ConfigAdapters.register(Component.class, new ComponentAdapter());
        ConfigAdapters.register(World.class, new WorldAdapter());
    }
}
