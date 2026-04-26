package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

/**
 * Audience wrapper for NeoForge command sources.
 */
public final class NeoForgeCommandAudience implements Audience {
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
    public NeoForgeCommandAudience(CommandSourceStack source, boolean broadcastToOps) {
        this(source, broadcastToOps, Mode.FEEDBACK);
    }

    /**
     * Creates a command audience with a custom mode.
     *
     * @param source command source
     * @param broadcastToOps whether to broadcast feedback to ops
     * @param mode feedback mode
     */
    public NeoForgeCommandAudience(CommandSourceStack source, boolean broadcastToOps, Mode mode) {
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
        return getPlayerSafe();
    }

    @Override
    public void send(Component component) {
        if (source == null || component == null) {
            return;
        }
        net.minecraft.network.chat.Component nativeComponent =
                Objects.requireNonNull(NeoForgeComponentSerializer.toNative(component), "nativeComponent");
        if (mode == Mode.ERROR) {
            source.sendFailure(nativeComponent);
        } else {
            source.sendSuccess(() -> nativeComponent, broadcastToOps);
        }
    }

    @Override
    public UUID id() {
        ServerPlayer player = getPlayerSafe();
        return player != null ? player.getUUID() : null;
    }

    @Override
    public boolean hasPermission(String permission) {
        return hasPermission(permission, 2);
    }

    @Override
    public boolean hasPermission(String permission, int fallbackOpLevel) {
        return NeoForgePermissionBridge.hasPermission(source, permission, fallbackOpLevel);
    }

    private ServerPlayer getPlayerSafe() {
        if (source == null) {
            return null;
        }
        try {
            return source.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }
}
