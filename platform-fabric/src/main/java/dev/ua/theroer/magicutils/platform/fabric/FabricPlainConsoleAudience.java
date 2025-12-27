package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Plain console audience that logs serialized text through PlatformLogger.
 */
final class FabricPlainConsoleAudience implements Audience {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final PlatformLogger logger;

    FabricPlainConsoleAudience(PlatformLogger logger) {
        this.logger = logger;
    }

    @Override
    public void send(Component component) {
        if (component == null || logger == null) {
            return;
        }
        logger.info(PLAIN.serialize(component));
    }
}
