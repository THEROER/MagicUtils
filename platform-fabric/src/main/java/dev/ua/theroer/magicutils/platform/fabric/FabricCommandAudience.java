package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Audience wrapper for Fabric command sources using sendFeedback.
 */
public final class FabricCommandAudience implements Audience {
    public enum Mode {
        FEEDBACK,
        ERROR
    }

    private final ServerCommandSource source;
    private final boolean broadcastToOps;
    private final Mode mode;

    public FabricCommandAudience(ServerCommandSource source, boolean broadcastToOps) {
        this(source, broadcastToOps, Mode.FEEDBACK);
    }

    public FabricCommandAudience(ServerCommandSource source, boolean broadcastToOps, Mode mode) {
        this.source = source;
        this.broadcastToOps = broadcastToOps;
        this.mode = mode != null ? mode : Mode.FEEDBACK;
    }

    public ServerCommandSource getSource() {
        return source;
    }

    public ServerPlayerEntity getPlayer() {
        return source != null ? source.getPlayer() : null;
    }

    @Override
    public void send(Component component) {
        if (source == null) {
            return;
        }
        if (mode == Mode.ERROR) {
            source.sendError(FabricComponentSerializer.toNative(component));
        } else {
            source.sendFeedback(() -> FabricComponentSerializer.toNative(component), broadcastToOps);
        }
    }

    @Override
    public UUID id() {
        if (source == null) {
            return null;
        }
        ServerPlayerEntity player = source.getPlayer();
        return player != null ? player.getUuid() : null;
    }
}
