package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Platform hooks required by the command engine.
 *
 * @param <S> sender type
 */
public interface CommandPlatform<S> {
    /**
     * Base sender type for this platform (e.g. CommandSender).
     *
     * @return sender class
     */
    Class<?> senderType();

    /**
     * Player type for this platform (e.g. Player), if available.
     *
     * @return player class or null
     */
    @Nullable
    default Class<?> playerType() {
        return null;
    }

    /**
     * Resolve a player instance for the given sender if one exists.
     *
     * @param sender sender
     * @return player instance or null
     */
    @Nullable
    default Object getPlayerSender(S sender) {
        return null;
    }

    /**
     * Resolve the human-readable sender name for logging.
     *
     * @param sender sender
     * @return name
     */
    String getName(S sender);

    /**
     * Check permission for a sender with default value awareness.
     *
     * @param sender sender
     * @param permission permission node
     * @param defaultValue permission default
     * @return true if granted
     */
    boolean hasPermission(S sender, String permission, MagicPermissionDefault defaultValue);

    /**
     * Register permission metadata if the platform supports it.
     *
     * @param node permission node
     * @param defaultValue default
     * @param description description
     */
    void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description);

    /**
     * Resolve sender argument for @Sender parameters.
     *
     * @param sender sender
     * @param argument argument metadata
     * @return resolved argument
     * @throws SenderMismatchException if sender is not allowed
     */
    Object resolveSenderArgument(S sender, CommandArgument argument) throws SenderMismatchException;

    /**
     * Checks if the provided type represents the base sender type.
     *
     * @param type type to check
     * @return true if it should be auto-filled by sender
     */
    default boolean isSenderType(Class<?> type) {
        return senderType().equals(type) || type == MagicSender.class;
    }

    /**
     * Check whether sender has any permission that starts with the given prefix.
     *
     * @param sender sender
     * @param prefixes prefixes to check
     * @return true if any permission matches
     */
    default boolean hasPermissionByPrefix(S sender, String... prefixes) {
        if (prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isEmpty()) {
                continue;
            }
            String withDot = prefix.endsWith(".") ? prefix : prefix + ".";
            if (hasPermission(sender, withDot + "*", MagicPermissionDefault.OP)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a human-readable description of the given sender kind.
     *
     * @param sender sender kind
     * @return description
     */
    default String describeSender(AllowedSender sender) {
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

    /**
     * Checks if the caller's sender kind is in the allowed list.
     *
     * @param allowed allowed sender kinds (null/empty means all)
     * @param calleeKind the caller's sender kind
     * @return true if allowed
     */
    default boolean isAllowedSender(AllowedSender[] allowed, AllowedSender calleeKind) {
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        for (AllowedSender a : allowed) {
            if (a == AllowedSender.ANY || a == calleeKind) {
                return true;
            }
        }
        return false;
    }

    /**
     * Infers the expected sender kind from a parameter type.
     *
     * @param type parameter type
     * @return inferred sender kind or {@link AllowedSender#ANY}
     */
    default AllowedSender inferSenderFromType(Class<?> type) {
        return AllowedSender.ANY;
    }

    /**
     * Builds a human-readable error message when the sender doesn't match.
     *
     * @param targetType the expected parameter type
     * @param allowed allowed sender kinds
     * @return error message
     */
    default String buildSenderError(Class<?> targetType, AllowedSender[] allowed) {
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
}
