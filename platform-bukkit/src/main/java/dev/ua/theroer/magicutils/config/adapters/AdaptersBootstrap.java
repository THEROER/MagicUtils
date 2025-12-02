package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers platform-specific config adapters.
 */
/**
 * Registers default config adapters for Bukkit platform types.
 */
public final class AdaptersBootstrap {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private AdaptersBootstrap() {
    }

    /**
     * Register built-in adapters for common Bukkit/Adventure types.
     */
    public static void registerDefaults() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ConfigAdapters.register(Component.class, new ComponentAdapter());
        ConfigAdapters.register(Material.class, new MaterialAdapter());
        ConfigAdapters.register(World.class, new WorldAdapter());
    }
}
