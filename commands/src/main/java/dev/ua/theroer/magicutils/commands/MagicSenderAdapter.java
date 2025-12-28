package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.Nullable;

public interface MagicSenderAdapter {
    boolean supports(Object sender);

    @Nullable
    MagicSender wrap(Object sender);
}
