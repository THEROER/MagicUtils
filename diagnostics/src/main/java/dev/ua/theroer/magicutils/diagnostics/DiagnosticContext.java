package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.TaskScheduler;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Context exposed to diagnostic checks.
 */
public interface DiagnosticContext {
    /**
     * Managed MagicUtils runtime.
     *
     * @return runtime container
     */
    MagicRuntime runtime();

    /**
     * Active platform adapter.
     *
     * @return platform adapter
     */
    Platform platform();

    /**
     * Logger core bound to the runtime.
     *
     * @return logger core
     */
    LoggerCore logger();

    /**
     * Requested execution mode.
     *
     * @return execution mode
     */
    DiagnosticMode mode();

    /**
     * Whether verbose output was requested.
     *
     * @return true when verbose output is enabled
     */
    boolean verbose();

    /**
     * Context start time.
     *
     * @return start instant
     */
    Instant startedAt();

    /**
     * Base working directory for file probes.
     *
     * @return working directory
     */
    Path workingDirectory();

    /**
     * Scheduler available to checks.
     *
     * @return task scheduler
     */
    TaskScheduler scheduler();

    /**
     * Finds a typed runtime component.
     *
     * @param type component type
     * @param <T> component type
     * @return optional component
     */
    <T> Optional<T> findComponent(Class<T> type);

    /**
     * Finds a named runtime component by type.
     *
     * @param name component name
     * @param type component type
     * @param <T> component type
     * @return optional component
     */
    <T> Optional<T> findNamedComponent(String name, Class<T> type);

    /**
     * Returns a component or throws when absent.
     *
     * @param type component type
     * @param <T> component type
     * @return component instance
     */
    <T> T requireComponent(Class<T> type);
}
