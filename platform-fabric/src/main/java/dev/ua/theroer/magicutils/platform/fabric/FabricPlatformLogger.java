package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.slf4j.Logger;

/**
 * SLF4J-backed platform logger for Fabric runtime.
 */
public final class FabricPlatformLogger implements PlatformLogger {
    private final Logger delegate;

    /**
     * Creates a logger wrapper.
     *
     * @param delegate slf4j logger
     */
    public FabricPlatformLogger(Logger delegate) {
        this.delegate = delegate;
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warn(message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        delegate.warn(message, throwable);
    }

    @Override
    public void error(String message) {
        delegate.error(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        delegate.error(message, throwable);
    }

    @Override
    public void debug(String message) {
        delegate.debug(message);
    }
}
