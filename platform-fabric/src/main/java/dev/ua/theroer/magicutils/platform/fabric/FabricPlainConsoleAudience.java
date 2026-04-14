package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.ConsoleColorSerializer;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import net.kyori.adventure.text.Component;

/**
 * Plain console audience that logs serialized text through PlatformLogger.
 */
final class FabricPlainConsoleAudience implements StructuredConsoleAudience {

    private final PlatformLogger logger;

    FabricPlainConsoleAudience(PlatformLogger logger) {
        this.logger = logger;
    }

    @Override
    public void send(Component component) {
        if (component == null || logger == null) {
            return;
        }
        sendConsole(component, new ConsoleMessageMetadata(LogLevel.INFO, null));
    }

    @Override
    public void sendConsole(Component component, ConsoleMessageMetadata metadata) {
        if (component == null || logger == null) {
            return;
        }
        String rendered = ConsoleColorSerializer.serialize(component);
        if (metadata == null || metadata.level() == null) {
            logger.info(rendered);
            return;
        }
        switch (metadata.level()) {
            case WARN -> logger.warn(rendered);
            case ERROR -> logger.error(rendered);
            case DEBUG, TRACE -> logger.debug(rendered);
            case SUCCESS, INFO -> logger.info(rendered);
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        // Console has unrestricted access by design.
        return true;
    }
}
