package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Audience wrapper for Fabric server players.
 */
public final class FabricAudience implements Audience {
    private final ServerPlayer player;

    /**
     * Creates an audience for the given player.
     *
     * @param player player instance
     */
    public FabricAudience(ServerPlayer player) {
        this.player = player;
    }

    /**
     * Returns the wrapped player instance.
     *
     * @return player instance
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    @Override
    public void send(Component component) {
        if (player == null) {
            return;
        }
        player.sendSystemMessage(FabricComponentSerializer.toNative(component), false);
    }

    @Override
    public UUID id() {
        return player != null ? player.getUUID() : null;
    }

    @Override
    public String name() {
        return player != null && player.getName() != null ? player.getName().getString() : null;
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission(permission, 2);
    }

    @Override
    public boolean hasPermission(String permission, int fallbackOpLevel) {
        return FabricPermissionBridge.hasPermission(player, permission, fallbackOpLevel);
    }
}
