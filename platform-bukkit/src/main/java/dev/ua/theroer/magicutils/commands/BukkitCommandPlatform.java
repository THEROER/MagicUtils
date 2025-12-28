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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bukkit-specific platform hooks for the command engine.
 */
public class BukkitCommandPlatform implements CommandPlatform<CommandSender> {
    private final CommandLogger logger;

    /**
     * Creates a Bukkit platform wrapper.
     *
     * @param logger command logger
     */
    public BukkitCommandPlatform(CommandLogger logger) {
        this.logger = logger != null ? logger : CommandLogger.noop();
    }

    /**
     * Wraps a Bukkit sender into {@link MagicSender}.
     *
     * @param sender Bukkit sender
     * @return wrapped sender or null if unavailable
     */
    public static @Nullable MagicSender wrapMagicSender(CommandSender sender) {
        if (sender == null) {
            return null;
        }
        return new BukkitMagicSender(sender);
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
            return new BukkitMagicSender(effective);
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

        private BukkitMagicSender(CommandSender sender) {
            this.sender = sender;
            this.audience = new BukkitAudienceWrapper(sender);
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
        if (sender instanceof ProxiedCommandSender proxied && proxied.getCallee() instanceof CommandSender callee) {
            return callee;
        }
        return sender;
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
            if (a == AllowedSender.PROXIED && proxied) {
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
