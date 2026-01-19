package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Console audience for NeoForge that targets the server command source when available.
 */
final class NeoForgeConsoleAudience implements Audience {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
        CommandSourceStack source = resolveSource();
        if (source != null) {
            new NeoForgeCommandAudience(source, false, NeoForgeCommandAudience.Mode.FEEDBACK).send(component);
            return;
        }
        if (fallbackLogger != null) {
            fallbackLogger.info(PLAIN.serialize(component));
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
