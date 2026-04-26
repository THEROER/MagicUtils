package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitAudienceWrapper;
import org.bukkit.Bukkit;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;


/**
 * Bukkit-specific platform hooks for the command engine.
 */
public class BukkitCommandPlatform implements CommandPlatform<CommandSender> {
    private final JavaPlugin plugin;
    private final CommandLogger logger;

    /**
     * Creates a Bukkit platform wrapper.
     *
     * @param plugin plugin instance
     * @param logger command logger
     */
    public BukkitCommandPlatform(JavaPlugin plugin, CommandLogger logger) {
        this.plugin = plugin;
        this.logger = logger != null ? logger : CommandLogger.noop();
    }

    /**
     * Creates a Bukkit platform wrapper.
     *
     * @param logger command logger
     */
    public BukkitCommandPlatform(CommandLogger logger) {
        this(null, logger);
    }

    /**
     * Wraps a Bukkit sender into {@link MagicSender}.
     *
     * @param sender Bukkit sender
     * @return wrapped sender or null if unavailable
     */
    public static @Nullable MagicSender wrapMagicSender(CommandSender sender) {
        return wrapMagicSender(sender, null);
    }

    /**
     * Wraps a Bukkit sender into {@link MagicSender} with a specific plugin context.
     *
     * @param sender Bukkit sender
     * @param plugin plugin instance
     * @return wrapped sender or null if unavailable
     */
    public static @Nullable MagicSender wrapMagicSender(CommandSender sender, @Nullable JavaPlugin plugin) {
        if (sender == null) {
            return null;
        }
        return new BukkitMagicSender(sender, plugin);
    }

    @Override
    public Class<?> senderType() {
        return CommandSender.class;
    }

    @Override
    public Class<?> playerType() {
        return Player.class;
    }

    @Override
    public @Nullable Object getPlayerSender(CommandSender sender) {
        return sender instanceof Player ? sender : null;
    }

    @Override
    public String getName(CommandSender sender) {
        return sender != null ? sender.getName() : "unknown";
    }

    @Override
    public boolean hasPermission(CommandSender sender, String permission, MagicPermissionDefault defaultValue) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        return sender != null && sender.hasPermission(permission);
    }

    @Override
    public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        if (node == null || node.isEmpty()) {
            return;
        }
        var pluginManager = Bukkit.getPluginManager();
        Permission existing = pluginManager.getPermission(node);
        PermissionDefault bukkitDefault = toBukkitDefault(defaultValue);
        if (existing == null) {
            Permission permission = new Permission(node, description != null ? description : "", bukkitDefault);
            pluginManager.addPermission(permission);
            logger.debug("Registered permission node: " + node + " (default " + bukkitDefault + ")");
        } else if (existing.getDefault() != bukkitDefault) {
            existing.setDefault(bukkitDefault);
            logger.debug("Updated permission default for node: " + node + " -> " + bukkitDefault);
        }
    }

    @Override
    public Object resolveSenderArgument(CommandSender sender, CommandArgument argument) throws SenderMismatchException {
        CommandSender effective = unwrapSender(sender);
        AllowedSender[] allowed = argument.getAllowedSenders();
        AllowedSender calleeKind = classifySender(effective);
        boolean proxied = sender instanceof ProxiedCommandSender;

        if (!isAllowedSender(allowed, calleeKind, proxied)) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();

        if (targetType.equals(CommandSender.class)) {
            return effective;
        }
        if (targetType.equals(MagicSender.class)) {
            return new BukkitMagicSender(effective, plugin);
        }
        if (targetType.equals(ProxiedCommandSender.class)) {
            if (sender instanceof ProxiedCommandSender p) {
                return p;
            }
        }
        if (targetType.equals(Player.class)) {
            if (effective instanceof Player p) {
                return p;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(ConsoleCommandSender.class)) {
            if (effective instanceof ConsoleCommandSender c) {
                return c;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(BlockCommandSender.class)) {
            if (effective instanceof BlockCommandSender b) {
                return b;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(CommandMinecart.class)) {
            if (effective instanceof CommandMinecart cart) {
                return cart;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(RemoteConsoleCommandSender.class)) {
            if (effective instanceof RemoteConsoleCommandSender r) {
                return r;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        if (targetType.isInstance(effective)) {
            return targetType.cast(effective);
        }

        throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
    }

    private static final class BukkitMagicSender implements MagicSender {
        private final CommandSender sender;
        private final Audience audience;

        private BukkitMagicSender(CommandSender sender, @Nullable JavaPlugin plugin) {
            this.sender = sender;
            this.audience = new BukkitAudienceWrapper(plugin, sender);
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
            if (permission == null || permission.isEmpty()) {
                return true;
            }
            return sender != null && sender.hasPermission(permission);
        }

        @Override
        public @Nullable String address() {
            if (sender instanceof org.bukkit.entity.Player player && player.getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
            return null;
        }

        @Override
        public Object handle() {
            return sender;
        }
    }

    @Override
    public boolean hasPermissionByPrefix(CommandSender sender, String... prefixes) {
        if (sender == null || prefixes == null || prefixes.length == 0) {
            return false;
        }
        for (PermissionAttachmentInfo pai : sender.getEffectivePermissions()) {
            if (!pai.getValue()) {
                continue;
            }
            String perm = pai.getPermission();
            if (perm == null) {
                continue;
            }
            for (String prefix : prefixes) {
                if (prefix != null && !prefix.isEmpty() && perm.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private CommandSender unwrapSender(CommandSender sender) {
        return (sender instanceof ProxiedCommandSender proxied && proxied.getCallee() != null)
                ? proxied.getCallee()
                : sender;
    }

    private AllowedSender classifySender(CommandSender sender) {
        if (sender instanceof Player) {
            return AllowedSender.PLAYER;
        }
        if (sender instanceof ConsoleCommandSender) {
            return AllowedSender.CONSOLE;
        }
        if (sender instanceof BlockCommandSender) {
            return AllowedSender.BLOCK;
        }
        if (sender instanceof CommandMinecart) {
            return AllowedSender.MINECART;
        }
        if (sender instanceof RemoteConsoleCommandSender) {
            return AllowedSender.REMOTE;
        }
        if (sender instanceof ProxiedCommandSender) {
            return AllowedSender.PROXIED;
        }
        return AllowedSender.ANY;
    }

    private boolean isAllowedSender(AllowedSender[] allowed, AllowedSender calleeKind, boolean proxied) {
        if (isAllowedSender(allowed, calleeKind)) {
            return true;
        }
        if (proxied) {
            for (AllowedSender a : allowed) {
                if (a == AllowedSender.PROXIED) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public AllowedSender inferSenderFromType(Class<?> type) {
        if (type.equals(Player.class)) {
            return AllowedSender.PLAYER;
        }
        if (type.equals(ConsoleCommandSender.class)) {
            return AllowedSender.CONSOLE;
        }
        if (type.equals(BlockCommandSender.class)) {
            return AllowedSender.BLOCK;
        }
        if (type.equals(CommandMinecart.class)) {
            return AllowedSender.MINECART;
        }
        if (type.equals(RemoteConsoleCommandSender.class)) {
            return AllowedSender.REMOTE;
        }
        if (type.equals(ProxiedCommandSender.class)) {
            return AllowedSender.PROXIED;
        }
        return AllowedSender.ANY;
    }

    private PermissionDefault toBukkitDefault(MagicPermissionDefault defaultValue) {
        if (defaultValue == null) {
            return PermissionDefault.OP;
        }
        return switch (defaultValue) {
            case FALSE -> PermissionDefault.FALSE;
            case NOT_OP -> PermissionDefault.NOT_OP;
            case TRUE -> PermissionDefault.TRUE;
            default -> PermissionDefault.OP;
        };
    }
}
