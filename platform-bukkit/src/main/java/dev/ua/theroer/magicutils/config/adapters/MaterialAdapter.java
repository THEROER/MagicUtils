package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import org.bukkit.Material;

/**
 * Adapter for Bukkit Material to simple string names.
 */
public class MaterialAdapter implements ConfigValueAdapter<Material> {
    /** Default constructor. */
    public MaterialAdapter() {
    }

    @Override
    public Material deserialize(Object value) {
        if (value == null) return null;
        return Material.matchMaterial(String.valueOf(value));
    }

    @Override
    public Object serialize(Material value) {
        return value != null ? value.name() : null;
    }
}
