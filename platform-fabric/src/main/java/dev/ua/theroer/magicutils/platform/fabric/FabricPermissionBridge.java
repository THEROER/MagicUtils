package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission bridge for Fabric audiences.
 *
 * <p>Uses Lucko Fabric Permissions API when present and falls back to
 * command-level permission checks.</p>
 */
public final class FabricPermissionBridge {
    private static final String PERMISSIONS_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";
    private static final Method CHECK_BOOLEAN;
    private static final Method CHECK_INT;
    private static final Method CHECK_SIMPLE;
    private static final Method HAS_PERMISSION_LEVEL;

    static {
        Class<?> permissions = ReflectiveAccess.loadClass(PERMISSIONS_CLASS).orElse(null);
        CHECK_BOOLEAN = permissions != null ? findMethod(permissions, boolean.class) : null;
        CHECK_INT = permissions != null ? findMethod(permissions, int.class) : null;
        CHECK_SIMPLE = permissions != null ? findMethod(permissions, null) : null;
        HAS_PERMISSION_LEVEL = resolvePermissionLevelMethod();
    }

    private FabricPermissionBridge() {
    }

    public static boolean hasPermission(ServerPlayer player, String permission, int opLevel) {
        if (player == null) {
            return false;
        }
        return hasPermission(player.createCommandSourceStack(), permission, opLevel);
    }

    public static boolean hasPermission(CommandSourceStack source, String permission, int opLevel) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        Boolean result = tryInvoke(source, permission, opLevel);
        if (result != null) {
            return result;
        }
        return hasCommandLevel(source, opLevel);
    }

    public static boolean hasCommandLevel(ServerPlayer player, int opLevel) {
        if (player == null) {
            return false;
        }
        return hasCommandLevel(player.createCommandSourceStack(), opLevel);
    }

    public static boolean hasCommandLevel(CommandSourceStack source, int opLevel) {
        if (source == null) {
            return false;
        }
        if (HAS_PERMISSION_LEVEL != null) {
            try {
                Object result = ReflectiveAccess.invoke(HAS_PERMISSION_LEVEL, source, opLevel).orElse(Boolean.FALSE);
                return result instanceof Boolean value && value;
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private static Boolean tryInvoke(CommandSourceStack source, String permission, int opLevel) {
        if (CHECK_BOOLEAN != null) {
            try {
                Object res = ReflectiveAccess.invoke(
                        CHECK_BOOLEAN,
                        null,
                        source,
                        permission,
                        hasCommandLevel(source, opLevel)
                ).orElse(null);
                return res instanceof Boolean value ? value : null;
            } catch (RuntimeException ignored) {
            }
        }
        if (CHECK_INT != null) {
            try {
                Object res = ReflectiveAccess.invoke(CHECK_INT, null, source, permission, opLevel).orElse(null);
                return res instanceof Boolean value ? value : null;
            } catch (RuntimeException ignored) {
            }
        }
        if (CHECK_SIMPLE != null) {
            try {
                Object res = ReflectiveAccess.invoke(CHECK_SIMPLE, null, source, permission).orElse(null);
                return res instanceof Boolean value ? value : null;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
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
            } else if (params.length == 3 && params[1] == String.class && params[2] == defaultType
                    && params[0].isAssignableFrom(CommandSourceStack.class)) {
                return method;
            }
        }
        return null;
    }

    private static Method resolvePermissionLevelMethod() {
        Method direct = ReflectiveAccess.publicMethod(
                CommandSourceStack.class,
                "hasPermissionLevel",
                int.class
        ).orElse(null);
        if (direct != null) {
            return direct;
        }
        Method candidate = null;
        for (Method method : CommandSourceStack.class.getMethods()) {
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != int.class) {
                continue;
            }
            if (method.getReturnType() != boolean.class) {
                continue;
            }
            if (candidate != null) {
                return null;
            }
            candidate = method;
        }
        if (candidate != null) {
            candidate.setAccessible(true);
        }
        return candidate;
    }
}
