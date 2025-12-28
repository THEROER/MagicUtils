package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.fabric.FabricCommandAudience;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fabric-specific platform hooks for the command engine.
 */
public class FabricCommandPlatform implements CommandPlatform<ServerCommandSource> {
    private final int opLevel;

    public FabricCommandPlatform() {
        this(2);
    }

    public FabricCommandPlatform(int opLevel) {
        this.opLevel = opLevel;
    }

    public static @Nullable MagicSender wrapMagicSender(ServerCommandSource sender, int opLevel) {
        if (sender == null) {
            return null;
        }
        return new FabricMagicSender(sender, opLevel);
    }

    @Override
    public Class<?> senderType() {
        return ServerCommandSource.class;
    }

    @Override
    public Class<?> playerType() {
        return ServerPlayerEntity.class;
    }

    @Override
    public @Nullable Object getPlayerSender(ServerCommandSource sender) {
        return getPlayerSafe(sender);
    }

    @Override
    public String getName(ServerCommandSource sender) {
        return sender != null ? sender.getName() : "unknown";
    }

    @Override
    public boolean hasPermission(ServerCommandSource sender, String permission, MagicPermissionDefault defaultValue) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        return FabricPermissionBridge.check(sender, permission, defaultValue, opLevel);
    }

    @Override
    public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        // No permission registry on Fabric by default.
    }

    @Override
    public Object resolveSenderArgument(ServerCommandSource sender, CommandArgument argument)
            throws SenderMismatchException {
        AllowedSender[] allowed = argument.getAllowedSenders();
        AllowedSender senderKind = classifySender(sender);
        if (!isAllowedSender(allowed, senderKind)) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();
        if (targetType.equals(ServerCommandSource.class)) {
            return sender;
        }
        if (targetType.equals(MagicSender.class)) {
            return new FabricMagicSender(sender, opLevel);
        }
        if (targetType.equals(ServerPlayerEntity.class)) {
            ServerPlayerEntity player = getPlayerSafe(sender);
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
        private final ServerCommandSource sender;
        private final Audience audience;
        private final int opLevel;

        private FabricMagicSender(ServerCommandSource sender, int opLevel) {
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
            return sender != null ? sender.getName() : "unknown";
        }

        @Override
        public boolean hasPermission(String permission) {
            return FabricPermissionBridge.check(sender, permission, opLevel);
        }

        @Override
        public Object handle() {
            return sender;
        }
    }

    private static final class FabricPermissionBridge {
        private static final String PERMISSIONS_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";
        private static final java.lang.reflect.Method CHECK_BOOLEAN;
        private static final java.lang.reflect.Method CHECK_INT;
        private static final java.lang.reflect.Method CHECK_SIMPLE;

        static {
            java.lang.reflect.Method checkBoolean = null;
            java.lang.reflect.Method checkInt = null;
            java.lang.reflect.Method checkSimple = null;
            Class<?> permissions = tryLoad(PERMISSIONS_CLASS);
            if (permissions != null) {
                checkBoolean = findMethod(permissions, boolean.class);
                checkInt = findMethod(permissions, int.class);
                checkSimple = findMethod(permissions, null);
            }
            CHECK_BOOLEAN = checkBoolean;
            CHECK_INT = checkInt;
            CHECK_SIMPLE = checkSimple;
        }

        private FabricPermissionBridge() {
        }

        static boolean check(ServerCommandSource sender, String permission, int opLevel) {
            if (permission == null || permission.isEmpty()) {
                return true;
            }
            if (sender == null) {
                return false;
            }
            Boolean result = tryInvoke(sender, permission, null, opLevel);
            if (result != null) {
                return result;
            }
            return sender.hasPermissionLevel(opLevel);
        }

        static boolean check(ServerCommandSource sender, String permission, MagicPermissionDefault defaultValue,
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

        private static Boolean tryInvoke(ServerCommandSource sender, String permission,
                                         MagicPermissionDefault defaultValue, int opLevel) {
            if (CHECK_BOOLEAN != null) {
                try {
                    boolean fallback = fallback(sender, defaultValue, opLevel);
                    Object res = CHECK_BOOLEAN.invoke(null, sender, permission, fallback);
                    return (Boolean) res;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            if (CHECK_INT != null) {
                try {
                    int fallback = fallbackLevel(defaultValue, opLevel);
                    Object res = CHECK_INT.invoke(null, sender, permission, fallback);
                    return (Boolean) res;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            if (CHECK_SIMPLE != null) {
                try {
                    Object res = CHECK_SIMPLE.invoke(null, sender, permission);
                    return (Boolean) res;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            return null;
        }

        private static boolean fallback(ServerCommandSource sender, MagicPermissionDefault defaultValue, int opLevel) {
            MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
            return switch (effective) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_OP -> !sender.hasPermissionLevel(opLevel);
                case OP -> sender.hasPermissionLevel(opLevel);
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

        private static java.lang.reflect.Method findMethod(Class<?> permissionsClass, Class<?> defaultType) {
            for (java.lang.reflect.Method method : permissionsClass.getMethods()) {
                if (!method.getName().equals("check")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (defaultType == null) {
                    if (params.length == 2 && params[1] == String.class
                            && params[0].isAssignableFrom(ServerCommandSource.class)) {
                        return method;
                    }
                } else {
                    if (params.length == 3 && params[1] == String.class && params[2] == defaultType
                            && params[0].isAssignableFrom(ServerCommandSource.class)) {
                        return method;
                    }
                }
            }
            return null;
        }

        private static Class<?> tryLoad(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
    }

    private ServerPlayerEntity getPlayerSafe(ServerCommandSource sender) {
        if (sender == null) {
            return null;
        }
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }

    private AllowedSender classifySender(ServerCommandSource sender) {
        if (getPlayerSafe(sender) != null) {
            return AllowedSender.PLAYER;
        }

        Entity entity = sender != null ? sender.getEntity() : null;
        if (entity instanceof CommandBlockMinecartEntity) {
            return AllowedSender.MINECART;
        }

        return AllowedSender.CONSOLE;
    }

    private boolean isAllowedSender(AllowedSender[] allowed, AllowedSender calleeKind) {
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        for (AllowedSender a : allowed) {
            if (a == AllowedSender.ANY) {
                return true;
            }
            if (a == calleeKind) {
                return true;
            }
        }
        return false;
    }

    private String buildSenderError(Class<?> targetType, AllowedSender[] allowed) {
        Set<AllowedSender> required = new LinkedHashSet<>();
        if (allowed != null) {
            required.addAll(Arrays.asList(allowed));
        }
        required.remove(AllowedSender.ANY);

        if (required.isEmpty()) {
            AllowedSender inferred = inferSenderFromType(targetType);
            if (inferred != AllowedSender.ANY) {
                required.add(inferred);
            }
        }

        if (required.isEmpty()) {
            return "This command cannot be used by this sender";
        }

        String messageBody = required.stream()
                .map(this::describeSender)
                .distinct()
                .collect(Collectors.joining(" or "));

        return "This command can only be used by " + messageBody;
    }

    private AllowedSender inferSenderFromType(Class<?> type) {
        if (type.equals(ServerPlayerEntity.class)) {
            return AllowedSender.PLAYER;
        }
        if (type.equals(CommandBlockMinecartEntity.class)) {
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

    private String describeSender(AllowedSender sender) {
        return switch (sender) {
            case PLAYER -> "players";
            case CONSOLE -> "console";
            case BLOCK -> "command blocks";
            case MINECART -> "command minecarts";
            case PROXIED -> "proxied senders";
            case REMOTE -> "remote console";
            default -> "valid senders";
        };
    }
}
