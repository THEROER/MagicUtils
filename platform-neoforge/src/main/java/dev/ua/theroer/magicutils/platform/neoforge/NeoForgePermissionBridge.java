package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.reflect.ReflectiveAccess;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission bridge for NeoForge audiences and raw senders.
 *
 * <p>NeoForge permission checks are node-based, so permission nodes must be registered before
 * PermissionAPI can delegate to installed backends such as LuckPerms. When a node is not yet
 * registered, this bridge falls back to command-level op checks to preserve existing behavior.</p>
 */
public final class NeoForgePermissionBridge {
    private static final int MAX_COMMAND_PERMISSION_LEVEL = 4;
    private static final String DEFAULT_MOD_ID = "magicutils";
    private static final Map<String, PermissionNode<Boolean>> REGISTERED_NODES = new ConcurrentHashMap<>();
    private static final Method LEGACY_HAS_PERMISSION_METHOD =
            ReflectiveAccess.publicMethod(CommandSourceStack.class, "hasPermission", int.class).orElse(null);
    private static final Method SOURCE_PERMISSIONS_METHOD =
            ReflectiveAccess.publicMethod(CommandSourceStack.class, "permissions").orElse(null);

    private NeoForgePermissionBridge() {
    }

    /**
     * Registers a boolean permission node with the given fallback op level.
     *
     * @param permission permission string
     * @param fallbackOpLevel op level to treat as granted when no permission backend answers
     */
    public static void register(String permission, int fallbackOpLevel) {
        if (permission == null || permission.isBlank()) {
            return;
        }
        REGISTERED_NODES.computeIfAbsent(permission, key -> createNode(key, fallbackOpLevel));
    }

    /**
     * Adds all previously registered nodes to the NeoForge permission gather event.
     *
     * @param event permission gather event
     */
    public static void addRegisteredNodes(Object event) {
        if (event == null || REGISTERED_NODES.isEmpty()) {
            return;
        }
        ReflectiveAccess.publicMethod(event.getClass(), "addNodes", PermissionNode[].class)
                .ifPresent(method -> ReflectiveAccess.invoke(
                        method,
                        event,
                        (Object) REGISTERED_NODES.values().toArray(PermissionNode[]::new)
                ));
    }

    /**
     * Checks player permission using NeoForge PermissionAPI when the node is registered and
     * otherwise falls back to command-level permissions.
     *
     * @param player player instance
     * @param permission permission string
     * @param fallbackOpLevel op level to treat as granted when no permission backend answers
     * @return true when allowed
     */
    public static boolean hasPermission(ServerPlayer player, String permission, int fallbackOpLevel) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        return hasPermission(player, permission, hasPermissionLevel(player.createCommandSourceStack(), fallbackOpLevel));
    }

    /**
     * Checks player permission using NeoForge PermissionAPI with an explicit fallback value.
     *
     * @param player player instance
     * @param permission permission string
     * @param fallbackAllowed fallback result when the node is not registered or the backend fails
     * @return true when allowed
     */
    public static boolean hasPermission(ServerPlayer player, String permission, boolean fallbackAllowed) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        PermissionNode<Boolean> node = REGISTERED_NODES.get(permission);
        if (node == null) {
            return fallbackAllowed;
        }
        try {
            return PermissionAPI.getPermission(player, node);
        } catch (RuntimeException ignored) {
            return fallbackAllowed;
        }
    }

    /**
     * Checks command source permission using NeoForge PermissionAPI for players and command-level
     * fallback for non-player sources.
     *
     * @param source command source
     * @param permission permission string
     * @param fallbackOpLevel op level to treat as granted when no permission backend answers
     * @return true when allowed
     */
    public static boolean hasPermission(CommandSourceStack source, String permission, int fallbackOpLevel) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        return hasPermission(source, permission, hasPermissionLevel(source, fallbackOpLevel));
    }

    /**
     * Checks command source permission with an explicit fallback value.
     *
     * @param source command source
     * @param permission permission string
     * @param fallbackAllowed fallback result when a backend cannot answer
     * @return true when allowed
     */
    public static boolean hasPermission(CommandSourceStack source, String permission, boolean fallbackAllowed) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        ServerPlayer player = getPlayerSafe(source);
        if (player != null) {
            return hasPermission(player, permission, fallbackAllowed);
        }
        return fallbackAllowed;
    }

    /**
     * Checks command-level permission on a source.
     *
     * @param source command source
     * @param fallbackOpLevel op level to test
     * @return true when granted
     */
    public static boolean hasPermissionLevel(CommandSourceStack source, int fallbackOpLevel) {
        if (source == null) {
            return false;
        }
        if (fallbackOpLevel <= 0) {
            return true;
        }
        if (fallbackOpLevel > MAX_COMMAND_PERMISSION_LEVEL) {
            return false;
        }
        Boolean legacyResult = ReflectiveAccess.invoke(LEGACY_HAS_PERMISSION_METHOD, source, fallbackOpLevel)
                .flatMap(value -> ReflectiveAccess.cast(value, Boolean.class))
                .orElse(null);
        if (legacyResult != null) {
            return legacyResult;
        }
        Object permissionSet = ReflectiveAccess.invoke(SOURCE_PERMISSIONS_METHOD, source).orElse(null);
        Object commandLevelPermission = PermissionApiCompat.commandLevelPermission(fallbackOpLevel);
        if (permissionSet == null || commandLevelPermission == null) {
            return false;
        }
        return ReflectiveAccess.invoke(PermissionApiCompat.permissionSetHasPermissionMethod(), permissionSet, commandLevelPermission)
                .flatMap(value -> ReflectiveAccess.cast(value, Boolean.class))
                .orElse(false);
    }

    private static PermissionNode<Boolean> createNode(String permission, int fallbackOpLevel) {
        String normalized = permission.trim();
        int separatorIndex = normalized.indexOf('.');
        String modId = separatorIndex > 0 ? normalized.substring(0, separatorIndex) : DEFAULT_MOD_ID;
        String nodeName = separatorIndex > 0 && separatorIndex + 1 < normalized.length()
                ? normalized.substring(separatorIndex + 1)
                : normalized;
        return new PermissionNode<>(
                modId,
                nodeName,
                PermissionTypes.BOOLEAN,
                (player, playerUuid, contexts) -> player != null
                        && hasPermissionLevel(player.createCommandSourceStack(), fallbackOpLevel)
        );
    }

    private static ServerPlayer getPlayerSafe(CommandSourceStack source) {
        if (source == null) {
            return null;
        }
        try {
            return source.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method resolvePermissionSetHasPermissionMethod() {
        Class<?> permissionSetClass = ReflectiveAccess.loadClass("net.minecraft.server.permissions.PermissionSet")
                .orElse(null);
        Class<?> permissionClass = ReflectiveAccess.loadClass("net.minecraft.server.permissions.Permission")
                .orElse(null);
        if (permissionSetClass == null || permissionClass == null) {
            return null;
        }
        return ReflectiveAccess.publicMethod(permissionSetClass, "hasPermission", permissionClass).orElse(null);
    }

    private static Map<Integer, Object> resolveCommandLevelPermissions() {
        Class<?> permissionsClass = ReflectiveAccess.loadClass("net.minecraft.server.permissions.Permissions")
                .orElse(null);
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
        ReflectiveAccess.publicField(permissionsClass, fieldName)
                .flatMap(field -> ReflectiveAccess.readField(field, null))
                .ifPresent(permission -> permissions.put(opLevel, permission));
    }

    private static final class PermissionApiCompat {
        private static final Method PERMISSION_SET_HAS_PERMISSION_METHOD = resolvePermissionSetHasPermissionMethod();
        private static final Map<Integer, Object> COMMAND_LEVEL_PERMISSIONS = resolveCommandLevelPermissions();

        private PermissionApiCompat() {
        }

        private static Method permissionSetHasPermissionMethod() {
            return PERMISSION_SET_HAS_PERMISSION_METHOD;
        }

        private static Object commandLevelPermission(int opLevel) {
            return COMMAND_LEVEL_PERMISSIONS.get(opLevel);
        }
    }
}
