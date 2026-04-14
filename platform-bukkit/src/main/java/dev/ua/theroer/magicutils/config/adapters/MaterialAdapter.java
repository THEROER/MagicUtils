package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Config adapter for Bukkit Material values.
 * Supports both namespaced keys (minecraft:shears) and enum names (SHEARS).
 */
public final class MaterialAdapter implements ConfigValueAdapter<Material> {
    /**
     * Creates a new instance of MaterialAdapter.
     */
    public MaterialAdapter() {
    }

    @Override
    public Material deserialize(Object value) {
        if (value == null) {
            return null;
        }

        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }

        Material material = Material.matchMaterial(raw, false);
        if (material != null) {
            return material;
        }

        if (raw.contains(":")) {
            String withoutNamespace = raw.substring(raw.indexOf(':') + 1);
            material = Material.matchMaterial(withoutNamespace, false);
            if (material != null) {
                return material;
            }
        }

        return Material.matchMaterial(raw.toUpperCase(Locale.ROOT), false);
    }

    @Override
    public Object serialize(Material value) {
        return value != null ? value.getKey().toString() : null;
    }
}
