package dev.ua.theroer.magicutils.platform.fabric;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Permission bridge for Fabric audiences.
 *
 * <p>Uses Lucko Fabric Permissions API when present and falls back to
 * command-level permission checks.</p>
 */
final class FabricPermissionBridge {
    private static final String PERMISSIONS_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";
    private static final java.lang.reflect.Method CHECK_BOOLEAN;
    private static final java.lang.reflect.Method CHECK_INT;
    private static final java.lang.reflect.Method CHECK_SIMPLE;
    private static final java.lang.reflect.Method HAS_PERMISSION_LEVEL;

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
        HAS_PERMISSION_LEVEL = resolvePermissionLevelMethod();
    }

    private FabricPermissionBridge() {
    }

    static boolean hasPermission(ServerPlayerEntity player, String permission, int opLevel) {
        if (player == null) {
            return false;
        }
        return hasPermission(player.getCommandSource(), permission, opLevel);
    }

    static boolean hasPermission(ServerCommandSource source, String permission, int opLevel) {
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
        return hasPermissionLevel(source, opLevel);
    }

    private static Boolean tryInvoke(ServerCommandSource source, String permission, int opLevel) {
        if (CHECK_BOOLEAN != null) {
            try {
                Object res = CHECK_BOOLEAN.invoke(null, source, permission, hasPermissionLevel(source, opLevel));
                return (Boolean) res;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (CHECK_INT != null) {
            try {
                Object res = CHECK_INT.invoke(null, source, permission, opLevel);
                return (Boolean) res;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        if (CHECK_SIMPLE != null) {
            try {
                Object res = CHECK_SIMPLE.invoke(null, source, permission);
                return (Boolean) res;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static boolean hasPermissionLevel(ServerCommandSource source, int opLevel) {
        if (source == null) {
            return false;
        }
        if (HAS_PERMISSION_LEVEL != null) {
            try {
                Object res = HAS_PERMISSION_LEVEL.invoke(source, opLevel);
                return res instanceof Boolean && (Boolean) res;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
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
            } else if (params.length == 3 && params[1] == String.class && params[2] == defaultType
                    && params[0].isAssignableFrom(ServerCommandSource.class)) {
                return method;
            }
        }
        return null;
    }

    private static java.lang.reflect.Method resolvePermissionLevelMethod() {
        try {
            java.lang.reflect.Method direct = ServerCommandSource.class.getMethod("hasPermissionLevel", int.class);
            direct.setAccessible(true);
            return direct;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> tryLoad(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
