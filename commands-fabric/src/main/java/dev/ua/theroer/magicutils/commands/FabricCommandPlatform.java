package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.fabric.FabricCommandAudience;
import dev.ua.theroer.magicutils.platform.fabric.FabricPermissionBridge;
import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Fabric-specific platform hooks for the command engine.
 */
public class FabricCommandPlatform implements CommandPlatform<CommandSourceStack> {
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
        return FabricPermissionDefaults.check(sender, permission, defaultValue, opLevel);
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
            return FabricPermissionDefaults.check(sender, permission, opLevel);
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

    private static final class FabricPermissionDefaults {
        private static final String PERMISSIONS_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";
        private static final Method CHECK_BOOLEAN;
        private static final Method CHECK_INT;
        private static final Method CHECK_SIMPLE;

        static {
            Class<?> permissions = ReflectiveAccess.loadClass(PERMISSIONS_CLASS).orElse(null);
            CHECK_BOOLEAN = permissions != null ? findMethod(permissions, boolean.class) : null;
            CHECK_INT = permissions != null ? findMethod(permissions, int.class) : null;
            CHECK_SIMPLE = permissions != null ? findMethod(permissions, null) : null;
        }

        private FabricPermissionDefaults() {
        }

        static boolean check(CommandSourceStack sender, String permission, int opLevel) {
            return FabricPermissionBridge.hasPermission(sender, permission, opLevel);
        }

        static boolean check(CommandSourceStack sender, String permission, MagicPermissionDefault defaultValue,
                             int opLevel) {
            if (permission == null || permission.isEmpty()) {
                return true;
            }
            if (sender == null) {
                return false;
            }
            Boolean result = tryInvoke(sender, permission, defaultValue, opLevel);
            if (result != null) {
                return result;
            }
            return fallback(sender, defaultValue, opLevel);
        }

        private static Boolean tryInvoke(CommandSourceStack sender, String permission,
                                         MagicPermissionDefault defaultValue, int opLevel) {
            if (CHECK_BOOLEAN != null) {
                try {
                    boolean fallback = fallback(sender, defaultValue, opLevel);
                    Object res = ReflectiveAccess.invoke(CHECK_BOOLEAN, null, sender, permission, fallback).orElse(null);
                    return res instanceof Boolean value ? value : null;
                } catch (RuntimeException ignored) {
                }
            }
            if (CHECK_INT != null) {
                try {
                    int fallback = fallbackLevel(defaultValue, opLevel);
                    Object res = ReflectiveAccess.invoke(CHECK_INT, null, sender, permission, fallback).orElse(null);
                    return res instanceof Boolean value ? value : null;
                } catch (RuntimeException ignored) {
                }
            }
            if (CHECK_SIMPLE != null) {
                try {
                    Object res = ReflectiveAccess.invoke(CHECK_SIMPLE, null, sender, permission).orElse(null);
                    return res instanceof Boolean value ? value : null;
                } catch (RuntimeException ignored) {
                }
            }
            return null;
        }

        private static boolean fallback(CommandSourceStack sender, MagicPermissionDefault defaultValue, int opLevel) {
            MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
            return switch (effective) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_OP -> !FabricPermissionBridge.hasCommandLevel(sender, opLevel);
                case OP -> FabricPermissionBridge.hasCommandLevel(sender, opLevel);
            };
        }

        private static int fallbackLevel(MagicPermissionDefault defaultValue, int opLevel) {
            MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
            return switch (effective) {
                case TRUE -> 0;
                case FALSE -> opLevel + 1;
                case NOT_OP -> 0;
                case OP -> opLevel;
            };
        }

        private static Method findMethod(Class<?> permissionsClass, Class<?> defaultType) {
            for (Method method : permissionsClass.getMethods()) {
                if (!method.getName().equals("check")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (defaultType == null) {
                    if (params.length == 2 && params[1] == String.class
                            && params[0].isAssignableFrom(CommandSourceStack.class)) {
                        return method;
                    }
                } else {
                    if (params.length == 3 && params[1] == String.class && params[2] == defaultType
                            && params[0].isAssignableFrom(CommandSourceStack.class)) {
                        return method;
                    }
                }
            }
            return null;
        }
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
