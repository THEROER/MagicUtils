package dev.ua.theroer.magicutils.platform.bungee;

import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.util.logging.Logger;

/**
 * JUL-backed platform logger for BungeeCord.
 */
public final class BungeePlatformLogger implements PlatformLogger {
    private final Logger delegate;

    /**
     * Creates a logger wrapper.
     *
     * @param delegate JUL logger
     */
    public BungeePlatformLogger(Logger delegate) {
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

    @Override
    public boolean isDebugEnabled() {
        return delegate.isLoggable(java.util.logging.Level.FINE);
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isLoggable(java.util.logging.Level.FINEST);
    }
}
