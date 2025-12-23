package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Audience wrapper for Fabric server players.
 */
public final class FabricAudience implements Audience {
    private final ServerPlayerEntity player;

    public FabricAudience(ServerPlayerEntity player) {
        this.player = player;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    @Override
    public void send(Component component) {
        if (player == null) {
            return;
        }
        player.sendMessage(FabricComponentSerializer.toNative(component), false);
    }

    @Override
    public UUID id() {
        return player != null ? player.getUuid() : null;
    }
}
