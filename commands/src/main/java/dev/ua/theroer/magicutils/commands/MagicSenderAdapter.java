package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.Nullable;

/**
 * Adapter for converting platform-specific sender instances into {@link MagicSender}.
 */
public interface MagicSenderAdapter {
    /**
     * Checks whether the adapter can handle the given sender instance.
     *
     * @param sender raw sender instance
     * @return true if supported
     */
    boolean supports(Object sender);

    /**
     * Wraps the sender into {@link MagicSender}.
     *
     * @param sender raw sender instance
     * @return wrapped sender or null if unsupported
     */
    @Nullable
    MagicSender wrap(Object sender);
}
