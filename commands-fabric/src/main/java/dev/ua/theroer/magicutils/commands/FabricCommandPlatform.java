package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.fabric.FabricCommandAudience;
import dev.ua.theroer.magicutils.platform.fabric.FabricPermissionBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Fabric-specific platform hooks for the command engine.
 */
public class FabricCommandPlatform implements CommandPlatform<CommandSourceStack> {
    private static final int DENIED_PERMISSION_LEVEL = 5;
    private static final String MODERN_MINECART_COMMAND_BLOCK_CLASS =
            "net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock";
    private static final String LEGACY_MINECART_COMMAND_BLOCK_CLASS =
            "net.minecraft.world.entity.vehicle.MinecartCommandBlock";
    private final int opLevel;

    /**
     * Creates a platform wrapper using op level 2.
     */
    public FabricCommandPlatform() {
        this(2);
    }

    /**
     * Creates a platform wrapper with a custom op level fallback.
     *
     * @param opLevel op level to use for permission fallback
     */
    public FabricCommandPlatform(int opLevel) {
        this.opLevel = opLevel;
    }

    /**
     * Wraps a Fabric sender into {@link MagicSender}.
     *
     * @param sender Fabric sender
     * @param opLevel op-level fallback for permissions
     * @return wrapped sender or null if unavailable
     */
    public static @Nullable MagicSender wrapMagicSender(CommandSourceStack sender, int opLevel) {
        if (sender == null) {
            return null;
        }
        return new FabricMagicSender(sender, opLevel);
    }

    @Override
    public Class<?> senderType() {
        return CommandSourceStack.class;
    }

    @Override
    public Class<?> playerType() {
        return ServerPlayer.class;
    }

    @Override
    public @Nullable Object getPlayerSender(CommandSourceStack sender) {
        return getPlayerSafe(sender);
    }

    @Override
    public String getName(CommandSourceStack sender) {
        return sender != null ? sender.getTextName() : "unknown";
    }

    @Override
    public boolean hasPermission(CommandSourceStack sender, String permission, MagicPermissionDefault defaultValue) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        return FabricPermissionBridge.hasPermission(
                (Object) sender,
                permission,
                fallbackAllowed(sender, defaultValue, opLevel),
                fallbackPermissionLevel(sender, defaultValue, opLevel)
        );
    }

    @Override
    public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        // No permission registry on Fabric by default.
    }

    @Override
    public Object resolveSenderArgument(CommandSourceStack sender, CommandArgument argument)
            throws SenderMismatchException {
        AllowedSender[] allowed = argument.getAllowedSenders();
        AllowedSender senderKind = classifySender(sender);
        if (!isAllowedSender(allowed, senderKind)) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();
        if (targetType.equals(CommandSourceStack.class)) {
            return sender;
        }
        if (targetType.equals(MagicSender.class)) {
            return new FabricMagicSender(sender, opLevel);
        }
        if (targetType.equals(ServerPlayer.class)) {
            ServerPlayer player = getPlayerSafe(sender);
            if (player != null) {
                return player;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Entity entity = sender != null ? sender.getEntity() : null;
        if (entity != null && targetType.isInstance(entity)) {
            return targetType.cast(entity);
        }

        if (sender != null && targetType.isInstance(sender)) {
            return targetType.cast(sender);
        }

        throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
    }

    private static final class FabricMagicSender implements MagicSender {
        private final CommandSourceStack sender;
        private final Audience audience;
        private final int opLevel;

        private FabricMagicSender(CommandSourceStack sender, int opLevel) {
            this.sender = sender;
            this.opLevel = opLevel;
            this.audience = new FabricCommandAudience(sender, false);
        }

        @Override
        public Audience audience() {
            return audience;
        }

        @Override
        public String name() {
            return sender != null ? sender.getTextName() : "unknown";
        }

        @Override
        public boolean hasPermission(String permission) {
            return hasPermission(permission, opLevel);
        }

        @Override
        public boolean hasPermission(String permission, int fallbackOpLevel) {
            return FabricPermissionBridge.hasPermission((Object) sender, permission, fallbackOpLevel);
        }

        @Override
        public @Nullable String address() {
            if (sender != null && sender.getPlayer() != null
                    && sender.getPlayer().connection != null
                    && sender.getPlayer().connection.getRemoteAddress() != null) {
                return sender.getPlayer().connection.getRemoteAddress().toString()
                        .replace("/", "").split(":")[0];
            }
            return null;
        }

        @Override
        public Object handle() {
            return sender;
        }
    }

    private static boolean fallbackAllowed(CommandSourceStack sender, MagicPermissionDefault defaultValue, int opLevel) {
        MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
        return switch (effective) {
            case TRUE -> true;
            case FALSE -> false;
            case NOT_OP -> !FabricPermissionBridge.hasCommandLevel((Object) sender, opLevel);
            case OP -> FabricPermissionBridge.hasCommandLevel((Object) sender, opLevel);
        };
    }

    private static int fallbackPermissionLevel(CommandSourceStack sender, MagicPermissionDefault defaultValue, int opLevel) {
        MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
        return switch (effective) {
            case TRUE -> 0;
            case FALSE -> DENIED_PERMISSION_LEVEL;
            case NOT_OP -> FabricPermissionBridge.hasCommandLevel((Object) sender, opLevel) ? DENIED_PERMISSION_LEVEL : 0;
            case OP -> opLevel;
        };
    }

    private ServerPlayer getPlayerSafe(CommandSourceStack sender) {
        if (sender == null) {
            return null;
        }
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }

    private AllowedSender classifySender(CommandSourceStack sender) {
        if (getPlayerSafe(sender) != null) {
            return AllowedSender.PLAYER;
        }

        Entity entity = sender != null ? sender.getEntity() : null;
        if (isMinecartCommandBlock(entity)) {
            return AllowedSender.MINECART;
        }

        return AllowedSender.CONSOLE;
    }

    @Override
    public AllowedSender inferSenderFromType(Class<?> type) {
        if (type.equals(ServerPlayer.class)) {
            return AllowedSender.PLAYER;
        }
        if (isMinecartCommandBlockType(type)) {
            return AllowedSender.MINECART;
        }
        String name = type.getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("commandblock")) {
            return AllowedSender.BLOCK;
        }
        if (name.contains("minecart")) {
            return AllowedSender.MINECART;
        }
        return AllowedSender.ANY;
    }

    private static boolean isMinecartCommandBlock(Entity entity) {
        return entity != null && isMinecartCommandBlockType(entity.getClass());
    }

    private static boolean isMinecartCommandBlockType(Class<?> type) {
        if (type == null) {
            return false;
        }
        String name = type.getName();
        return MODERN_MINECART_COMMAND_BLOCK_CLASS.equals(name)
                || LEGACY_MINECART_COMMAND_BLOCK_CLASS.equals(name);
    }

}
