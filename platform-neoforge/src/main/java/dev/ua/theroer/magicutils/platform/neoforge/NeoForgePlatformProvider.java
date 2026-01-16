package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Platform provider for NeoForge (minimal).
 *
 * <p>Note: this adapter does not currently expose online players or a main-thread
 * scheduler. Calls to those APIs will log a warning and fall back to safe defaults.</p>
 */
public class NeoForgePlatformProvider implements Platform {
    private final Path configDir;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final AtomicBoolean warnedOnlinePlayers = new AtomicBoolean(false);
    private final AtomicBoolean warnedRunOnMain = new AtomicBoolean(false);

    /**
     * Create a minimal NeoForge platform adapter using SLF4J for logging.
     */
    public NeoForgePlatformProvider() {
        Logger slf4j = LoggerFactory.getLogger("MagicUtils-NeoForge");
        this.logger = new NeoForgePlatformLogger(slf4j);
        this.consoleAudience = new NeoForgeAudience(slf4j, null);
        this.configDir = Path.of("config");
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    @Override
    public Audience console() {
        return consoleAudience;
    }

    @Override
    public Collection<Audience> onlinePlayers() {
        if (warnedOnlinePlayers.compareAndSet(false, true)) {
            logger.warn("NeoForge platform does not expose online players; returning empty list.");
        }
        return Collections.emptyList();
    }

    @Override
    public void runOnMain(Runnable task) {
        if (warnedRunOnMain.compareAndSet(false, true)) {
            logger.warn("NeoForge platform does not provide a main-thread scheduler; running inline.");
        }
        task.run();
    }

    @Override
    public boolean isMainThread() {
        return false;
    }

    @Override
    public ThreadContext threadContext() {
        return ThreadContext.UNKNOWN;
    }
}
