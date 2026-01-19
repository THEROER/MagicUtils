package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.neoforge.NeoForgeCommandAudience;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NeoForge-specific platform hooks for the command engine.
 */
public class NeoForgeCommandPlatform implements CommandPlatform<CommandSourceStack> {
    private static final Method GET_TEXT_NAME = resolveMethod(CommandSourceStack.class, "getTextName");
    private static final Method GET_DISPLAY_NAME = resolveMethod(CommandSourceStack.class, "getDisplayName");
    private static final Method GET_NAME = resolveMethod(CommandSourceStack.class, "getName");

    private final int opLevel;

    /**
     * Creates a platform wrapper using op level 2.
     */
    public NeoForgeCommandPlatform() {
        this(2);
    }

    /**
     * Creates a platform wrapper with a custom op level fallback.
     *
     * @param opLevel op level to use for permission fallback
     */
    public NeoForgeCommandPlatform(int opLevel) {
        this.opLevel = opLevel;
    }

    /**
     * Wraps a NeoForge sender into {@link MagicSender}.
     *
     * @param sender command sender
     * @param opLevel op-level fallback for permissions
     * @return wrapped sender or null if unavailable
     */
    public static @Nullable MagicSender wrapMagicSender(CommandSourceStack sender, int opLevel) {
        if (sender == null) {
            return null;
        }
        return new NeoForgeMagicSender(sender, opLevel);
    }

    @Override
    public Class<?> senderType() {
        return CommandSourceStack.class;
    }

    @Override
    public Class<?> playerType() {
        return ServerPlayer.class;
    }

    @Override
    public @Nullable Object getPlayerSender(CommandSourceStack sender) {
        return getPlayerSafe(sender);
    }

    @Override
    public String getName(CommandSourceStack sender) {
        if (sender == null) {
            return "unknown";
        }
        String resolved = resolveName(sender);
        return resolved != null ? resolved : "unknown";
    }

    @Override
    public boolean hasPermission(CommandSourceStack sender, String permission, MagicPermissionDefault defaultValue) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        return fallback(sender, defaultValue, opLevel);
    }

    @Override
    public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        // No permission registry on NeoForge by default.
    }

    @Override
    public Object resolveSenderArgument(CommandSourceStack sender, CommandArgument argument)
            throws SenderMismatchException {
        AllowedSender[] allowed = argument.getAllowedSenders();
        AllowedSender senderKind = classifySender(sender);
        if (!isAllowedSender(allowed, senderKind)) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();
        if (targetType.equals(CommandSourceStack.class)) {
            return sender;
        }
        if (targetType.equals(MagicSender.class)) {
            return new NeoForgeMagicSender(sender, opLevel);
        }
        if (targetType.equals(ServerPlayer.class)) {
            ServerPlayer player = getPlayerSafe(sender);
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

    private static final class NeoForgeMagicSender implements MagicSender {
        private final CommandSourceStack sender;
        private final Audience audience;
        private final int opLevel;

        private NeoForgeMagicSender(CommandSourceStack sender, int opLevel) {
            this.sender = sender;
            this.opLevel = opLevel;
            this.audience = new NeoForgeCommandAudience(sender, false);
        }

        @Override
        public Audience audience() {
            return audience;
        }

        @Override
        public String name() {
            return sender != null ? resolveName(sender) : "unknown";
        }

        @Override
        public boolean hasPermission(String permission) {
            return fallback(sender, MagicPermissionDefault.OP, opLevel);
        }

        @Override
        public Object handle() {
            return sender;
        }
    }

    private static boolean fallback(CommandSourceStack sender, MagicPermissionDefault defaultValue, int opLevel) {
        MagicPermissionDefault effective = defaultValue != null ? defaultValue : MagicPermissionDefault.OP;
        return switch (effective) {
            case TRUE -> true;
            case FALSE -> false;
            case NOT_OP -> !hasPermissionLevel(sender, opLevel);
            case OP -> hasPermissionLevel(sender, opLevel);
        };
    }

    private static boolean hasPermissionLevel(CommandSourceStack sender, int opLevel) {
        if (sender == null) {
            return false;
        }
        try {
            return sender.hasPermission(opLevel);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ServerPlayer getPlayerSafe(CommandSourceStack sender) {
        if (sender == null) {
            return null;
        }
        try {
            return sender.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static AllowedSender classifySender(CommandSourceStack sender) {
        if (getPlayerSafe(sender) != null) {
            return AllowedSender.PLAYER;
        }

        Entity entity = sender != null ? sender.getEntity() : null;
        if (entity instanceof MinecartCommandBlock) {
            return AllowedSender.MINECART;
        }

        return AllowedSender.CONSOLE;
    }

    private static boolean isAllowedSender(AllowedSender[] allowed, AllowedSender calleeKind) {
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

    private static String buildSenderError(Class<?> targetType, AllowedSender[] allowed) {
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
                .map(NeoForgeCommandPlatform::describeSender)
                .distinct()
                .collect(Collectors.joining(" or "));

        return "This command can only be used by " + messageBody;
    }

    private static AllowedSender inferSenderFromType(Class<?> type) {
        if (type.equals(ServerPlayer.class)) {
            return AllowedSender.PLAYER;
        }
        if (type.equals(MinecartCommandBlock.class)) {
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

    private static String describeSender(AllowedSender sender) {
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

    private static String resolveName(CommandSourceStack sender) {
        Object value = invokeMethod(sender, GET_TEXT_NAME);
        String name = componentToString(value);
        if (name != null && !name.isBlank()) {
            return name;
        }
        value = invokeMethod(sender, GET_DISPLAY_NAME);
        name = componentToString(value);
        if (name != null && !name.isBlank()) {
            return name;
        }
        value = invokeMethod(sender, GET_NAME);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    private static String componentToString(Object value) {
        if (value instanceof Component component) {
            return component.getString();
        }
        if (value instanceof net.minecraft.network.chat.Component component) {
            return component.getString();
        }
        if (value instanceof String str) {
            return str;
        }
        return value != null ? String.valueOf(value) : null;
    }

    private static Object invokeMethod(CommandSourceStack sender, Method method) {
        if (sender == null || method == null) {
            return null;
        }
        try {
            return method.invoke(sender);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method resolveMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
