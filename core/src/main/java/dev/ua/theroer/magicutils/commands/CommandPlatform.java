package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.Nullable;

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
        return senderType().equals(type);
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
}
