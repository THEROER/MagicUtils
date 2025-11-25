package dev.ua.theroer.magicutils.platform;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Abstraction over runtime platform (Bukkit, Fabric, NeoForge, etc.).
 */
public interface Platform {
    /**
     * Base directory for configs/lang files.
     */
    Path configDir();

    /**
        * Primary logger for platform.
        */
    PlatformLogger logger();

    /**
        * Audience representing console/server.
        */
    Audience console();

    /**
        * Snapshot of online player audiences, if applicable.
        */
    Collection<Audience> onlinePlayers();

    /**
        * Executes a task on the main thread.
        */
    void runOnMain(Runnable task);

    /**
        * @return true if current thread is main/platform thread.
        */
    boolean isMainThread();
}
