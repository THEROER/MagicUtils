package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;

/**
 * Console audience capable of receiving structured log metadata directly.
 */
public interface StructuredConsoleAudience extends Audience {
    /**
     * Sends a console message with explicit metadata.
     *
     * @param component rendered component
     * @param metadata structured console metadata
     */
    void sendConsole(Component component, ConsoleMessageMetadata metadata);
}
