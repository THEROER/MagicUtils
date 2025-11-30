package dev.ua.theroer.magicutils.platform;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Abstraction over runtime platform (Bukkit, Fabric, NeoForge, etc.).
 */
public interface Platform {
    /**
     * Base directory for configs/lang files.
     *
     * @return config path
     */
    Path configDir();

    /**
     * Primary logger for platform.
     *
     * @return platform logger
     */
    PlatformLogger logger();

    /**
     * Audience representing console/server.
     *
     * @return console audience
     */
    Audience console();

    /**
     * Snapshot of online player audiences, if applicable.
     *
     * @return online audiences collection (may be empty)
     */
    Collection<Audience> onlinePlayers();

    /**
     * Executes a task on the main thread.
     *
     * @param task work to run
     */
    void runOnMain(Runnable task);

    /**
     * Determine if the current thread is the platform main thread.
     *
     * @return true if current thread is main/platform thread.
     */
    boolean isMainThread();
}
