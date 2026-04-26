package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.TaskScheduler;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

final class DefaultDiagnosticContext implements DiagnosticContext {
    private final MagicRuntime runtime;
    private final LoggerCore logger;
    private final DiagnosticMode mode;
    private final boolean verbose;
    private final Instant startedAt;
    private final TaskScheduler scheduler;

    DefaultDiagnosticContext(
            MagicRuntime runtime,
            DiagnosticMode mode,
            boolean verbose,
            Instant startedAt
    ) {
        this.runtime = runtime;
        this.logger = runtime.requireComponent(LoggerCore.class);
        this.mode = mode;
        this.verbose = verbose;
        this.startedAt = startedAt;
        this.scheduler = runtime.platform().scheduler();
    }

    @Override
    public MagicRuntime runtime() {
        return runtime;
    }

    @Override
    public Platform platform() {
        return runtime.platform();
    }

    @Override
    public LoggerCore logger() {
        return logger;
    }

    @Override
    public DiagnosticMode mode() {
        return mode;
    }

    @Override
    public boolean verbose() {
        return verbose;
    }

    @Override
    public Instant startedAt() {
        return startedAt;
    }

    @Override
    public Path workingDirectory() {
        return runtime.platform().configDir();
    }

    @Override
    public TaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public <T> Optional<T> findComponent(Class<T> type) {
        return runtime.findComponent(type);
    }

    @Override
    public <T> Optional<T> findNamedComponent(String name, Class<T> type) {
        return runtime.findNamedComponent(name, type);
    }

    @Override
    public <T> T requireComponent(Class<T> type) {
        return runtime.requireComponent(type);
    }
}
