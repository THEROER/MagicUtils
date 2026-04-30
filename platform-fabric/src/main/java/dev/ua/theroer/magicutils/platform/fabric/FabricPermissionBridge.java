package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission bridge for Fabric audiences.
 *
 * <p>Uses Lucko Fabric Permissions API when present and falls back to
 * command-level permission checks.</p>
 */
public final class FabricPermissionBridge {
    private static final int MAX_COMMAND_PERMISSION_LEVEL = 4;
    private static final String PERMISSIONS_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";
    private static final String MINECRAFT_NAMESPACE = FabricReflectionMappings.NAMED_NAMESPACE;
    private static final String COMMAND_SOURCE_STACK_CLASS = "net.minecraft.commands.CommandSourceStack";
    private static final String SERVER_PLAYER_CLASS = "net.minecraft.server.level.ServerPlayer";
    private static final String PERMISSION_SET_CLASS = "net.minecraft.server.permissions.PermissionSet";
    private static final String PERMISSION_CLASS = "net.minecraft.server.permissions.Permission";
    private static final String COMMAND_PERMISSIONS_CLASS = "net.minecraft.server.permissions.Permissions";
    private static final String COMMAND_SOURCE_PERMISSIONS_DESCRIPTOR = "()Lnet/minecraft/server/permissions/PermissionSet;";
    private static final String CREATE_COMMAND_SOURCE_DESCRIPTOR = "()Lnet/minecraft/commands/CommandSourceStack;";
    private static final String PERMISSION_SET_HAS_PERMISSION_DESCRIPTOR = "(Lnet/minecraft/server/permissions/Permission;)Z";
    private static final String PERMISSION_FIELD_DESCRIPTOR = "Lnet/minecraft/server/permissions/Permission;";
    private static final String PERMISSION_SET_FIELD_DESCRIPTOR = "Lnet/minecraft/server/permissions/PermissionSet;";
    private static final Method[] CHECK_BOOLEAN;
    private static final Method[] CHECK_INT;
    private static final Method[] CHECK_SIMPLE;
    private static final Map<Class<?>, Optional<Method>> PERMISSION_LEVEL_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> SOURCE_PERMISSIONS_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> COMMAND_SOURCE_METHODS = new ConcurrentHashMap<>();

    static {
        FabricReflectionMappings.install();
        Class<?> permissions = ReflectiveAccess.loadClass(PERMISSIONS_CLASS).orElse(null);
        CHECK_BOOLEAN = permissions != null ? findMethods(permissions, boolean.class) : new Method[0];
        CHECK_INT = permissions != null ? findMethods(permissions, int.class) : new Method[0];
        CHECK_SIMPLE = permissions != null ? findMethods(permissions, null) : new Method[0];
    }

    private FabricPermissionBridge() {
    }

    /**
     * Checks a raw Minecraft command source or player with explicit fallback semantics.
     *
     * @param source command source or player instance
     * @param permission permission node
     * @param fallbackAllowed fallback result when no permission backend answers
     * @param fallbackOpLevel fallback command permission level
     * @return true when access is allowed
     */
    public static boolean hasPermission(Object source, String permission, boolean fallbackAllowed, int fallbackOpLevel) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        Object commandSource = commandSource(source);
        if (commandSource == null) {
            return false;
        }
        Boolean result = tryInvoke(commandSource, permission, fallbackAllowed, fallbackOpLevel);
        if (result != null) {
            return result;
        }
        return fallbackAllowed;
    }

    /**
     * Checks a raw Minecraft command source or player using command-level fallback.
     *
     * @param source command source or player instance
     * @param permission permission node
     * @param opLevel fallback command permission level
     * @return true when access is allowed
     */
    public static boolean hasPermission(Object source, String permission, int opLevel) {
        return hasPermission(source, permission, hasCommandLevel(source, opLevel), opLevel);
    }

    /**
     * Checks player permission using command-level fallback.
     *
     * @param player player instance
     * @param permission permission node
     * @param opLevel fallback command permission level
     * @return true when access is allowed
     */
    public static boolean hasPermission(ServerPlayer player, String permission, int opLevel) {
        if (player == null) {
            return false;
        }
        return hasPermission(player.createCommandSourceStack(), permission, opLevel);
    }

    /**
     * Checks player permission with explicit fallback semantics.
     *
     * @param player player instance
     * @param permission permission node
     * @param fallbackAllowed fallback result when no permission backend answers
     * @param fallbackOpLevel fallback command permission level
     * @return true when access is allowed
     */
    public static boolean hasPermission(ServerPlayer player, String permission, boolean fallbackAllowed, int fallbackOpLevel) {
        if (player == null) {
            return false;
        }
        return hasPermission(player.createCommandSourceStack(), permission, fallbackAllowed, fallbackOpLevel);
    }

    /**
     * Checks command source permission using command-level fallback.
     *
     * @param source command source
     * @param permission permission node
     * @param opLevel fallback command permission level
     * @return true when access is allowed
     */
    public static boolean hasPermission(CommandSourceStack source, String permission, int opLevel) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        return hasPermission(source, permission, hasCommandLevel(source, opLevel), opLevel);
    }

    /**
     * Checks command source permission with explicit fallback semantics.
     *
     * @param source command source
     * @param permission permission node
     * @param fallbackAllowed fallback result when no permission backend answers
     * @param fallbackOpLevel fallback command permission level
     * @return true when access is allowed
     */
    public static boolean hasPermission(
            CommandSourceStack source,
            String permission,
            boolean fallbackAllowed,
            int fallbackOpLevel
    ) {
        return hasPermission((Object) source, permission, fallbackAllowed, fallbackOpLevel);
    }

    /**
     * Checks whether a player has the requested command permission level.
     *
     * @param player player instance
     * @param opLevel command permission level
     * @return true when the level is granted
     */
    public static boolean hasCommandLevel(ServerPlayer player, int opLevel) {
        if (player == null) {
            return false;
        }
        return hasCommandLevel((Object) player.createCommandSourceStack(), opLevel);
    }

    /**
     * Checks whether a raw Minecraft command source or player has the requested command level.
     *
     * @param source command source or player instance
     * @param opLevel command permission level
     * @return true when the level is granted
     */
    public static boolean hasCommandLevel(Object source, int opLevel) {
        Object commandSource = commandSource(source);
        if (commandSource == null) {
            return false;
        }
        if (opLevel <= 0) {
            return true;
        }
        if (opLevel > MAX_COMMAND_PERMISSION_LEVEL) {
            return false;
        }

        Boolean methodResult = invokePermissionLevelMethod(commandSource, opLevel);
        if (methodResult != null) {
            return methodResult;
        }

        Object permissionSet = permissions(commandSource);
        if (PermissionSetConstants.isAllPermissions(permissionSet)) {
            return true;
        }
        if (PermissionSetConstants.isNoPermissions(permissionSet)) {
            return false;
        }
        Object commandPermission = PermissionApiCompat.commandLevelPermission(opLevel);
        if (permissionSet == null || commandPermission == null) {
            return false;
        }
        return ReflectiveAccess.invoke(PermissionApiCompat.permissionSetHasPermissionMethod(), permissionSet, commandPermission)
                .flatMap(value -> ReflectiveAccess.cast(value, Boolean.class))
                .orElse(false);
    }

    /**
     * Checks whether a command source has the requested command permission level.
     *
     * @param source command source
     * @param opLevel command permission level
     * @return true when the level is granted
     */
    public static boolean hasCommandLevel(CommandSourceStack source, int opLevel) {
        return hasCommandLevel((Object) source, opLevel);
    }

    private static Boolean tryInvoke(
            Object source,
            String permission,
            boolean fallbackAllowed,
            int fallbackOpLevel
    ) {
        for (Method method : CHECK_BOOLEAN) {
            try {
                Object res = ReflectiveAccess.invoke(
                        method,
                        null,
                        source,
                        permission,
                        fallbackAllowed
                ).orElse(null);
                return res instanceof Boolean value ? value : null;
            } catch (RuntimeException ignored) {
            }
        }
        for (Method method : CHECK_INT) {
            try {
                Object res = ReflectiveAccess.invoke(method, null, source, permission, fallbackOpLevel).orElse(null);
                return res instanceof Boolean value ? value : null;
            } catch (RuntimeException ignored) {
            }
        }
        for (Method method : CHECK_SIMPLE) {
            try {
                Object res = ReflectiveAccess.invoke(method, null, source, permission).orElse(null);
                return res instanceof Boolean value ? value : null;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Boolean invokePermissionLevelMethod(Object source, int opLevel) {
        Method method = PERMISSION_LEVEL_METHODS.computeIfAbsent(
                source.getClass(),
                type -> Optional.ofNullable(resolvePermissionLevelMethod(type))
        ).orElse(null);
        return ReflectiveAccess.invoke(method, source, opLevel)
                .flatMap(value -> ReflectiveAccess.cast(value, Boolean.class))
                .orElse(null);
    }

    private static Method[] findMethods(Class<?> permissionsClass, Class<?> defaultType) {
        List<Method> methods = new ArrayList<>();
        for (Method method : permissionsClass.getMethods()) {
            if (!method.getName().equals("check")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (defaultType == null) {
                if (params.length == 2 && params[1] == String.class) {
                    method.setAccessible(true);
                    methods.add(method);
                }
            } else if (params.length == 3 && params[1] == String.class && params[2] == defaultType
                    && !params[0].isPrimitive()) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        return methods.toArray(Method[]::new);
    }

    private static Method resolvePermissionLevelMethod(Class<?> sourceType) {
        Method direct = firstMappedPublicMethod(sourceType, int.class, "hasPermissionLevel", "hasPermission");
        if (direct != null) {
            return direct;
        }
        Method candidate = null;
        for (Method method : sourceType.getMethods()) {
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

    private static Object commandSource(Object source) {
        if (source == null) {
            return null;
        }
        Object commandSource = ReflectiveAccess.invoke(commandSourceMethod(source), source).orElse(null);
        return commandSource != null ? commandSource : source;
    }

    private static Method commandSourceMethod(Object source) {
        return COMMAND_SOURCE_METHODS.computeIfAbsent(
                source.getClass(),
                type -> ReflectiveAccess.publicMappedMethod(
                        type,
                        MINECRAFT_NAMESPACE,
                        SERVER_PLAYER_CLASS,
                        "createCommandSourceStack",
                        CREATE_COMMAND_SOURCE_DESCRIPTOR
                )
        ).orElse(null);
    }

    private static Object permissions(Object source) {
        Method method = SOURCE_PERMISSIONS_METHODS.computeIfAbsent(
                source.getClass(),
                type -> ReflectiveAccess.publicMappedMethod(
                        type,
                        MINECRAFT_NAMESPACE,
                        COMMAND_SOURCE_STACK_CLASS,
                        "permissions",
                        COMMAND_SOURCE_PERMISSIONS_DESCRIPTOR
                )
        ).orElse(null);
        return ReflectiveAccess.invoke(method, source).orElse(null);
    }

    private static Method firstMappedPublicMethod(Class<?> type, Class<?> parameterType, String... names) {
        for (String name : names) {
            Method method = parameterType == null
                    ? ReflectiveAccess.publicMappedMethod(
                            type,
                            MINECRAFT_NAMESPACE,
                            COMMAND_SOURCE_STACK_CLASS,
                            name,
                            null
                    ).orElse(null)
                    : ReflectiveAccess.publicMappedMethod(
                            type,
                            MINECRAFT_NAMESPACE,
                            COMMAND_SOURCE_STACK_CLASS,
                            name,
                            "(I)Z",
                            parameterType
                    ).orElse(null);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static Method resolvePermissionSetHasPermissionMethod() {
        Class<?> permissionSetClass;
        Class<?> permissionClass;
        try {
            permissionSetClass = ReflectiveAccess.loadMappedClass(MINECRAFT_NAMESPACE, PERMISSION_SET_CLASS).orElse(null);
            permissionClass = ReflectiveAccess.loadMappedClass(MINECRAFT_NAMESPACE, PERMISSION_CLASS).orElse(null);
        } catch (LinkageError error) {
            return null;
        }
        if (permissionSetClass == null || permissionClass == null) {
            return null;
        }
        return ReflectiveAccess.publicMappedMethod(
                permissionSetClass,
                MINECRAFT_NAMESPACE,
                PERMISSION_SET_CLASS,
                "hasPermission",
                PERMISSION_SET_HAS_PERMISSION_DESCRIPTOR,
                permissionClass
        ).orElse(null);
    }

    private static Map<Integer, Object> resolveCommandLevelPermissions() {
        Class<?> permissionsClass;
        try {
            permissionsClass = ReflectiveAccess.loadMappedClass(MINECRAFT_NAMESPACE, COMMAND_PERMISSIONS_CLASS).orElse(null);
        } catch (LinkageError error) {
            return Map.of();
        }
        if (permissionsClass == null) {
            return Map.of();
        }

        Map<Integer, Object> permissions = new HashMap<>();
        putCommandLevelPermission(permissions, permissionsClass, 1, "COMMANDS_MODERATOR");
        putCommandLevelPermission(permissions, permissionsClass, 2, "COMMANDS_GAMEMASTER");
        putCommandLevelPermission(permissions, permissionsClass, 3, "COMMANDS_ADMIN");
        putCommandLevelPermission(permissions, permissionsClass, 4, "COMMANDS_OWNER");
        return Map.copyOf(permissions);
    }

    private static void putCommandLevelPermission(
            Map<Integer, Object> permissions,
            Class<?> permissionsClass,
            int opLevel,
            String fieldName
    ) {
        Object permission = ReflectiveAccess.publicMappedField(
                        permissionsClass,
                        MINECRAFT_NAMESPACE,
                        COMMAND_PERMISSIONS_CLASS,
                        fieldName,
                        PERMISSION_FIELD_DESCRIPTOR
                )
                .flatMap(field -> ReflectiveAccess.readField(field, null))
                .orElse(null);
        if (permission != null) {
            permissions.put(opLevel, permission);
        }
    }

    private static final class PermissionSetConstants {
        private static final Object NO_PERMISSIONS = readPermissionSetConstant("NO_PERMISSIONS");
        private static final Object ALL_PERMISSIONS = readPermissionSetConstant("ALL_PERMISSIONS");

        private PermissionSetConstants() {
        }

        private static boolean isNoPermissions(Object permissionSet) {
            return permissionSet != null && permissionSet == NO_PERMISSIONS;
        }

        private static boolean isAllPermissions(Object permissionSet) {
            return permissionSet != null && permissionSet == ALL_PERMISSIONS;
        }

        private static Object readPermissionSetConstant(String fieldName) {
            try {
                Class<?> permissionSetClass = ReflectiveAccess.loadMappedClass(MINECRAFT_NAMESPACE, PERMISSION_SET_CLASS)
                        .orElse(null);
                return ReflectiveAccess.publicMappedField(
                                permissionSetClass,
                                MINECRAFT_NAMESPACE,
                                PERMISSION_SET_CLASS,
                                fieldName,
                                PERMISSION_SET_FIELD_DESCRIPTOR
                        )
                        .flatMap(field -> ReflectiveAccess.readField(field, null))
                        .orElse(null);
            } catch (LinkageError error) {
                return null;
            }
        }
    }

    private static final class PermissionApiCompat {
        private PermissionApiCompat() {
        }

        private static Method permissionSetHasPermissionMethod() {
            return PermissionMethodHolder.PERMISSION_SET_HAS_PERMISSION_METHOD;
        }

        private static Object commandLevelPermission(int opLevel) {
            return CommandLevelPermissionsHolder.COMMAND_LEVEL_PERMISSIONS.get(opLevel);
        }

        private static final class PermissionMethodHolder {
            private static final Method PERMISSION_SET_HAS_PERMISSION_METHOD = resolvePermissionSetHasPermissionMethod();
        }

        private static final class CommandLevelPermissionsHolder {
            private static final Map<Integer, Object> COMMAND_LEVEL_PERMISSIONS = resolveCommandLevelPermissions();
        }
    }
}
