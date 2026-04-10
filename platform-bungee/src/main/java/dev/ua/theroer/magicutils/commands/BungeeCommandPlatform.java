package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.bungee.BungeeComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.UUID;

final class BungeeCommandPlatform implements CommandPlatform<CommandSender> {
    private final ProxyServer proxy;
    private final CommandLogger logger;

    BungeeCommandPlatform(ProxyServer proxy, CommandLogger logger) {
        this.proxy = proxy;
        this.logger = logger != null ? logger : CommandLogger.noop();
    }

    static @Nullable MagicSender wrapMagicSender(CommandSender source, ProxyServer proxy) {
        if (source == null) {
            return null;
        }
        return new BungeeMagicSender(source, proxy);
    }

    @Override
    public Class<?> senderType() {
        return CommandSender.class;
    }

    @Override
    public Class<?> playerType() {
        return ProxiedPlayer.class;
    }

    @Override
    public @Nullable Object getPlayerSender(CommandSender sender) {
        return sender instanceof ProxiedPlayer ? sender : null;
    }

    @Override
    public String getName(CommandSender sender) {
        return sender != null ? sender.getName() : "unknown";
    }

    @Override
    public boolean hasPermission(CommandSender sender, String permission, MagicPermissionDefault defaultValue) {
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
            case OP -> isConsole(sender);
            case NOT_OP -> !isConsole(sender);
            case FALSE -> false;
        };
    }

    @Override
    public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        if (node == null || node.isBlank()) {
            return;
        }
        logger.debug("Bungee permission metadata is not registrable: " + node);
    }

    @Override
    public Object resolveSenderArgument(CommandSender sender, CommandArgument argument) throws SenderMismatchException {
        AllowedSender[] allowed = argument.getAllowedSenders();
        boolean proxied = sender != null && !(sender instanceof ProxiedPlayer);
        if (!isAllowedSender(allowed, classifySender(sender), proxied)) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();
        if (targetType.equals(CommandSender.class)) {
            return sender;
        }
        if (targetType.equals(MagicSender.class)) {
            return new BungeeMagicSender(sender, proxy);
        }
        if (targetType.equals(ProxiedPlayer.class)) {
            if (sender instanceof ProxiedPlayer player) {
                return player;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.isInstance(sender)) {
            return targetType.cast(sender);
        }

        throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
    }

    @Override
    public boolean hasPermissionByPrefix(CommandSender sender, String... prefixes) {
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

    private AllowedSender classifySender(CommandSender sender) {
        if (sender instanceof ProxiedPlayer) {
            return AllowedSender.PLAYER;
        }
        return AllowedSender.CONSOLE;
    }

    private boolean isAllowedSender(AllowedSender[] allowed, AllowedSender senderKind, boolean proxied) {
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        for (AllowedSender item : allowed) {
            if (item == AllowedSender.ANY || item == senderKind) {
                return true;
            }
            if (item == AllowedSender.PROXIED && proxied) {
                return true;
            }
        }
        return false;
    }

    private boolean isConsole(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        if (proxy != null && sender == proxy.getConsole()) {
            return true;
        }
        return !(sender instanceof ProxiedPlayer);
    }

    private String buildSenderError(Class<?> targetType, AllowedSender[] allowed) {
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

    private static final class BungeeMagicSender implements MagicSender {
        private final CommandSender source;
        private final ProxyServer proxy;
        private final Audience audience;

        private BungeeMagicSender(CommandSender source, ProxyServer proxy) {
            this.source = source;
            this.proxy = proxy;
            this.audience = new BungeeSourceAudience(source);
        }

        @Override
        public Audience audience() {
            return audience;
        }

        @Override
        public String name() {
            return source != null ? source.getName() : "unknown";
        }

        @Override
        public UUID id() {
            if (source instanceof ProxiedPlayer player) {
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
            if (source instanceof Connection connection
                    && connection.getSocketAddress() instanceof InetSocketAddress address
                    && address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
            return null;
        }

        @Override
        public Object handle() {
            return source;
        }
    }

    private static final class BungeeSourceAudience implements Audience {
        private final CommandSender source;

        private BungeeSourceAudience(CommandSender source) {
            this.source = source;
        }

        @Override
        public void send(net.kyori.adventure.text.Component component) {
            if (source == null || component == null) {
                return;
            }
            source.sendMessage(BungeeComponentSerializer.toBaseComponents(component));
        }

        @Override
        public UUID id() {
            if (source instanceof ProxiedPlayer player) {
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
