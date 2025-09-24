package dev.ua.theroer.magicutils.integrations;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Real PlaceholderAPI integration
 * IMPORTANT: PlaceholderAPI imports are only in this class!
 * This class is loaded only if PlaceholderAPI is present
 */
public final class PapiEngine implements PlaceholderEngine {

    /**
     * Creates a new PlaceholderAPI engine
     */
    public PapiEngine() {
        // Default constructor
    }

    @Override
    public String apply(@Nullable Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}