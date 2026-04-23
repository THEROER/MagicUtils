package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * Audience wrapper for Fabric command sources using sendFeedback.
 */
public final class FabricCommandAudience implements Audience {
    /**
     * Message dispatch mode.
     */
    public enum Mode {
        /** Sends feedback via standard feedback channel. */
        FEEDBACK,
        /** Sends feedback via error channel. */
        ERROR
    }

    private final CommandSourceStack source;
    private final boolean broadcastToOps;
    private final Mode mode;

    /**
     * Creates a command audience using feedback mode.
     *
     * @param source command source
     * @param broadcastToOps whether to broadcast feedback to ops
     */
    public FabricCommandAudience(CommandSourceStack source, boolean broadcastToOps) {
        this(source, broadcastToOps, Mode.FEEDBACK);
    }

    /**
     * Creates a command audience with a custom mode.
     *
     * @param source command source
     * @param broadcastToOps whether to broadcast feedback to ops
     * @param mode feedback mode
     */
    public FabricCommandAudience(CommandSourceStack source, boolean broadcastToOps, Mode mode) {
        this.source = source;
        this.broadcastToOps = broadcastToOps;
        this.mode = mode != null ? mode : Mode.FEEDBACK;
    }

    /**
     * Returns the command source.
     *
     * @return command source
     */
    public CommandSourceStack getSource() {
        return source;
    }

    /**
     * Returns the player when the source is a player.
     *
     * @return player instance or null
     */
    public ServerPlayer getPlayer() {
        return source != null ? source.getPlayer() : null;
    }

    @Override
    public void send(Component component) {
        if (source == null) {
            return;
        }
        if (mode == Mode.ERROR) {
            source.sendFailure(FabricComponentSerializer.toNative(component));
        } else {
            source.sendSuccess(() -> FabricComponentSerializer.toNative(component), broadcastToOps);
        }
    }

    @Override
    public UUID id() {
        if (source == null) {
            return null;
        }
        ServerPlayer player = source.getPlayer();
        return player != null ? player.getUUID() : null;
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission(permission, 2);
    }

    @Override
    public boolean hasPermission(String permission, int fallbackOpLevel) {
        return FabricPermissionBridge.hasPermission(source, permission, fallbackOpLevel);
    }
}
