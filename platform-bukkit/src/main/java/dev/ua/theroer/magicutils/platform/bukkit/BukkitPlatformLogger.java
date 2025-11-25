package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.util.logging.Logger;

/**
 * Lightweight bridge to Bukkit's plugin logger.
 */
public class BukkitPlatformLogger implements PlatformLogger {
    private final Logger delegate;

    public BukkitPlatformLogger(Logger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warning(message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        delegate.log(java.util.logging.Level.WARNING, message, throwable);
    }

    @Override
    public void error(String message) {
        delegate.severe(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        delegate.log(java.util.logging.Level.SEVERE, message, throwable);
    }

    @Override
    public void debug(String message) {
        delegate.fine(message);
    }
}
