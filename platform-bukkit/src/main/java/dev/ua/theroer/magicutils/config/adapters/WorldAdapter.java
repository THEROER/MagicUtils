package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Adapter for Bukkit World using world name.
 */
public class WorldAdapter implements ConfigValueAdapter<World> {
    /** Default constructor. */
    public WorldAdapter() {
    }

    @Override
    public World deserialize(Object value) {
        if (value == null) return null;
        return Bukkit.getWorld(String.valueOf(value));
    }

    @Override
    public Object serialize(World value) {
        return value != null ? value.getName() : null;
    }
}
