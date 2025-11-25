package dev.ua.theroer.magicutils.platform.neoforge;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Platform provider for NeoForge (minimal).
 */
public class NeoForgePlatformProvider implements Platform {
    private final Path configDir;
    private final PlatformLogger logger;
    private final Audience consoleAudience;

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
        return Collections.emptyList();
    }

    @Override
    public void runOnMain(Runnable task) {
        task.run();
    }

    @Override
    public boolean isMainThread() {
        return true;
    }
}
