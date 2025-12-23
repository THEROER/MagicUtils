package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import net.kyori.adventure.text.Component;

/**
 * Console audience that logs through PlatformLogger.
 */
public final class FabricConsoleAudience implements Audience {
    private final PlatformLogger logger;

    public FabricConsoleAudience(PlatformLogger logger) {
        this.logger = logger;
    }

    @Override
    public void send(Component component) {
        if (logger == null) {
            return;
        }
        logger.info(FabricComponentSerializer.toPlain(component));
    }
}
