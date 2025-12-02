package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * MiniMessage-based adapter for Adventure Component in configs.
 */
public class ComponentAdapter implements ConfigValueAdapter<Component> {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Default constructor. */
    public ComponentAdapter() {
    }

    @Override
    public Component deserialize(Object value) {
        if (value == null) return null;
        return MM.deserialize(String.valueOf(value));
    }

    @Override
    public Object serialize(Component value) {
        return value != null ? MM.serialize(value) : null;
    }
}
