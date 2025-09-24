package dev.ua.theroer.magicutils.integrations;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for placeholder processing
 */
public interface PlaceholderEngine {

    /**
     * Processes placeholders in text
     * 
     * @param player player for placeholder context (can be null)
     * @param text   text with placeholders
     * @return text with processed placeholders
     */
    String apply(@Nullable Player player, String text);
}