package dev.ua.theroer.magicutils.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.ua.theroer.magicutils.platform.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

final class VelocityCommandPlatform implements CommandPlatform<CommandSource> {
    private final CommandLogger logger;

    VelocityCommandPlatform(CommandLogger logger) {
        this.logger = logger != null ? logger : CommandLogger.noop();
    }

    static @Nullable MagicSender wrapMagicSender(CommandSource source) {
        if (source == null) {
            return null;
        }
        return new VelocityMagicSender(source);
    }

    @Override
    public Class<?> senderType() {
        return CommandSource.class;
    }

    @Override
    public Class<?> playerType() {
        return Player.class;
    }

    @Override
    public @Nullable Object getPlayerSender(CommandSource sender) {
        return sender instanceof Player ? sender : null;
    }

    @Override
    public String getName(CommandSource sender) {
        if (sender instanceof Player player) {
            return player.getUsername();
        }
        if (sender instanceof ConsoleCommandSource) {
            return "console";
        }
        return sender != null ? sender.getClass().getSimpleName() : "unknown";
    }

    @Override
    public boolean hasPermission(CommandSource sender, String permission, MagicPermissionDefault defaultValue) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (sender != null && sender.hasPermission(permission)) {
            return true;
        }
        if (defaultValue == null) {
            return false;
        }
        return switch (defaultValue) {
            case TRUE -> true;
            case OP -> sender instanceof ConsoleCommandSource;
            case NOT_OP -> !(sender instanceof ConsoleCommandSource);
            case FALSE -> false;
        };
    }

    @Override
    public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        if (node == null || node.isBlank()) {
            return;
        }
    }

    @Override
    public Object resolveSenderArgument(CommandSource sender, CommandArgument argument) throws SenderMismatchException {
        AllowedSender[] allowed = argument.getAllowedSenders();
        if (!isAllowedSender(allowed, classifySender(sender))) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();
        if (targetType.equals(CommandSource.class)) {
            return sender;
        }
        if (targetType.equals(MagicSender.class)) {
            return new VelocityMagicSender(sender);
        }
        if (targetType.equals(Player.class)) {
            if (sender instanceof Player player) {
                return player;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(ConsoleCommandSource.class)) {
            if (sender instanceof ConsoleCommandSource console) {
                return console;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.isInstance(sender)) {
            return targetType.cast(sender);
        }

        throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
    }

    @Override
    public boolean hasPermissionByPrefix(CommandSource sender, String... prefixes) {
        if (sender == null || prefixes == null || prefixes.length == 0) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (sender.hasPermission(prefix)
                    || sender.hasPermission(prefix + "*")
                    || sender.hasPermission(prefix + ".*")) {
                return true;
            }
        }
        return false;
    }

    private AllowedSender classifySender(CommandSource sender) {
        if (sender instanceof Player) {
            return AllowedSender.PLAYER;
        }
        if (sender instanceof ConsoleCommandSource) {
            return AllowedSender.CONSOLE;
        }
        return AllowedSender.ANY;
    }

    @Override
    public String buildSenderError(Class<?> targetType, AllowedSender[] allowed) {
        String expected = targetType != null ? targetType.getSimpleName() : "unknown";
        if (allowed == null || allowed.length == 0) {
            return "Sender type mismatch. Expected: " + expected;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Sender type mismatch. Expected one of: ");
        for (int i = 0; i < allowed.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(allowed[i].name().toLowerCase());
        }
        builder.append(" (target=").append(expected).append(')');
        return builder.toString();
    }

    private static final class VelocityMagicSender implements MagicSender {
        private final CommandSource source;
        private final Audience audience;

        private VelocityMagicSender(CommandSource source) {
            this.source = source;
            this.audience = new VelocitySourceAudience(source);
        }

        @Override
        public Audience audience() {
            return audience;
        }

        @Override
        public String name() {
            if (source instanceof Player player) {
                return player.getUsername();
            }
            if (source instanceof ConsoleCommandSource) {
                return "console";
            }
            return source != null ? source.getClass().getSimpleName() : "unknown";
        }

        @Override
        public UUID id() {
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
            return null;
        }

        @Override
        public boolean hasPermission(String permission) {
            if (permission == null || permission.isBlank()) {
                return true;
            }
            return source != null && source.hasPermission(permission);
        }

        @Override
        public @Nullable String address() {
            if (source instanceof Player player && player.getRemoteAddress() != null) {
                return player.getRemoteAddress().getAddress().getHostAddress();
            }
            return null;
        }

        @Override
        public Object handle() {
            return source;
        }
    }

    private static final class VelocitySourceAudience implements Audience {
        private final CommandSource source;

        private VelocitySourceAudience(CommandSource source) {
            this.source = source;
        }

        @Override
        public void send(Component component) {
            if (source == null || component == null) {
                return;
            }
            source.sendMessage(component);
        }

        @Override
        public UUID id() {
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
            return null;
        }

        @Override
        public boolean hasPermission(String permission) {
            if (permission == null || permission.isBlank()) {
                return true;
            }
            return source != null && source.hasPermission(permission);
        }
    }
}
