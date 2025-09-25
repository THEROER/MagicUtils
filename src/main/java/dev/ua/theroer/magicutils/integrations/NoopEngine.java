package dev.ua.theroer.magicutils.integrations;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * No-op placeholder engine that returns text unchanged
 * Used when PlaceholderAPI is unavailable
 */
public final class NoopEngine implements PlaceholderEngine {

    /**
     * Creates a new no-op placeholder engine
     */
    public NoopEngine() {
        // Default constructor
    }

    @Override
    public String apply(@Nullable Player player, String text) {
        return text;
    }
}