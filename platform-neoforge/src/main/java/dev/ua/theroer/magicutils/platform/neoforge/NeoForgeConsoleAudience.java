package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.logger.ConsoleColorSerializer;
import dev.ua.theroer.magicutils.logger.ConsoleMessageMetadata;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.StructuredConsoleAudience;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Console audience for NeoForge that targets the server command source when available.
 */
final class NeoForgeConsoleAudience implements StructuredConsoleAudience {

    private final Supplier<MinecraftServer> serverSupplier;
    private final Logger fallbackLogger;

    NeoForgeConsoleAudience(Supplier<MinecraftServer> serverSupplier, Logger fallbackLogger) {
        this.serverSupplier = serverSupplier;
        this.fallbackLogger = fallbackLogger;
    }

    @Override
    public void send(Component component) {
        if (component == null) {
            return;
        }
        sendConsole(component, new ConsoleMessageMetadata(LogLevel.INFO, null));
    }

    @Override
    public void sendConsole(Component component, ConsoleMessageMetadata metadata) {
        if (component == null || metadata == null) {
            return;
        }
        CommandSourceStack source = resolveSource();
        if (source != null) {
            new NeoForgeCommandAudience(source, false, NeoForgeCommandAudience.Mode.FEEDBACK).send(component);
            return;
        }
        if (fallbackLogger != null) {
            fallbackLogger.info(ConsoleColorSerializer.serialize(component));
        }
    }

    private CommandSourceStack resolveSource() {
        if (serverSupplier == null) {
            return null;
        }
        try {
            MinecraftServer server = serverSupplier.get();
            return server != null ? server.createCommandSourceStack() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
