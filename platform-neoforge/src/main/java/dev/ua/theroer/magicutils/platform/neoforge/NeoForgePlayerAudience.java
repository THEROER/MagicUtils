package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Audience wrapper for NeoForge server players.
 */
public final class NeoForgePlayerAudience implements Audience {
    private final ServerPlayer player;

    /**
     * Creates an audience for the given player.
     *
     * @param player player instance
     */
    public NeoForgePlayerAudience(ServerPlayer player) {
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
        if (player == null || component == null) {
            return;
        }
        net.minecraft.network.chat.Component nativeComponent = NeoForgeComponentSerializer.toNative(component);
        player.sendSystemMessage(nativeComponent);
    }

    @Override
    public UUID id() {
        return player != null ? player.getUUID() : null;
    }
}
