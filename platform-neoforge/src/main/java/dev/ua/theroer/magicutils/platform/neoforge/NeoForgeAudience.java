package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Minimal audience wrapper for NeoForge; routes messages to logger.
 */
public class NeoForgeAudience implements Audience {
    private final Logger logger;
    private final UUID id;

    /**
     * Wrap an SLF4J logger as a NeoForge audience.
     *
     * @param logger SLF4J logger to output to
     * @param id audience identifier (may be null for console)
     */
    public NeoForgeAudience(Logger logger, UUID id) {
        this.logger = logger;
        this.id = id;
    }

    @Override
    public void send(Component component) {
        logger.info(component.toString());
    }

    @Override
    public UUID id() {
        return id;
    }
}
