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
        MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
        return switch (effective) {
            case TRUE -> true;
            case FALSE -> false;
            case NOT_OP -> !sender.hasPermissionLevel(opLevel);
            case OP -> sender.hasPermissionLevel(opLevel);
        };
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
            if (permission == null || permission.isEmpty()) {
                return true;
            }
            return sender != null && sender.hasPermissionLevel(opLevel);
        }

        @Override
        public Object handle() {
            return sender;
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
