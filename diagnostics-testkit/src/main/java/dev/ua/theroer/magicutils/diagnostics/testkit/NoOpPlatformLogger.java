package dev.ua.theroer.magicutils.diagnostics.testkit;

import dev.ua.theroer.magicutils.platform.PlatformLogger;

/**
 * No-op {@link PlatformLogger} for tests that exercise diagnostics without a real
 * platform. Every logging method silently discards its input, keeping test output
 * clean while still satisfying the {@link PlatformLogger} contract required by
 * {@link dev.ua.theroer.magicutils.platform.Platform#logger()}.
 *
 * <p>Use {@link #INSTANCE} directly; the enum is stateless and safe to share.</p>
 */
public enum NoOpPlatformLogger implements PlatformLogger {
    /**
     * Shared singleton instance.
     */
    INSTANCE;

    @Override
    public void info(String message) {
        // no-op
    }

    @Override
    public void warn(String message) {
        // no-op
    }

    @Override
    public void warn(String message, Throwable throwable) {
        // no-op
    }

    @Override
    public void error(String message) {
        // no-op
    }

    @Override
    public void error(String message, Throwable throwable) {
        // no-op
    }

    @Override
    public void debug(String message) {
        // no-op
    }
}
