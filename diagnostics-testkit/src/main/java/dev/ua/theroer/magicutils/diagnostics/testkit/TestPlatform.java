package dev.ua.theroer.magicutils.diagnostics.testkit;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * In-memory {@link Platform} implementation for JUnit-style tests that need to run
 * diagnostics without a real Minecraft server.
 *
 * <p>The platform is deliberately minimal and platform-agnostic: it exposes a
 * caller-provided config directory, a {@link NoOpPlatformLogger}, no console or
 * player audiences, and a single-thread {@link TaskScheduler}. {@link #runOnMain(Runnable)}
 * runs work inline on the calling thread and {@link #isMainThread()} always reports
 * {@code true}, which keeps synchronous diagnostics checks deterministic.</p>
 *
 * <p>It also implements {@link ConfigFormatProvider}, defaulting to the {@code json}
 * config extension so that {@code ConfigManager} produces stable, human-readable
 * fixtures under the temporary directory.</p>
 *
 * <p>Prefer {@link DiagnosticsTestHarness}, which wires this platform together with
 * a {@code ConfigManager}, {@code LoggerCore} and {@code MagicRuntime}. Use
 * {@code TestPlatform} directly only when you need finer control over the wiring.
 * Callers that construct it directly must invoke {@link #shutdown()} to release the
 * backing scheduler thread.</p>
 */
public final class TestPlatform implements Platform, ConfigFormatProvider {
    private final Path configDir;
    private final TaskScheduler scheduler;

    /**
     * Creates a test platform rooted at the given config directory.
     *
     * @param configDir base directory for configs/lang files (typically a JUnit {@code @TempDir});
     *                  must not be {@code null}
     */
    public TestPlatform(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
        this.scheduler = TaskSchedulers.create("magicutils-diagnostics-testkit", null);
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public PlatformLogger logger() {
        return NoOpPlatformLogger.INSTANCE;
    }

    @Override
    public Audience console() {
        return null;
    }

    @Override
    public Collection<Audience> onlinePlayers() {
        return List.of();
    }

    @Override
    public void runOnMain(Runnable task) {
        if (task != null) {
            task.run();
        }
    }

    @Override
    public boolean isMainThread() {
        return true;
    }

    @Override
    public TaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public String defaultConfigExtension() {
        return "json";
    }

    /**
     * Shuts down the backing task scheduler. Safe to call multiple times.
     *
     * <p>{@link DiagnosticsTestHarness#close()} calls this automatically; only invoke
     * it manually when you construct the platform yourself.</p>
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
