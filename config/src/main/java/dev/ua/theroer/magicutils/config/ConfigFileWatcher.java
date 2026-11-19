package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Watches the directories of registered config files (one {@link WatchKey} per
 * directory, reference counted) and reports changed files by {@link ConfigKey}.
 */
final class ConfigFileWatcher {
    private final Map<Path, Set<ConfigKey>> fileIndex = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> directoryWatchKeys = new ConcurrentHashMap<>();
    private final Map<Path, Integer> directoryRefCounts = new ConcurrentHashMap<>();
    private final Object watcherLock = new Object();
    private WatchService watchService;
    private ExecutorService watcherExecutor;
    private volatile boolean watchServiceUnavailable = false;
    private volatile boolean watchServiceWarned = false;

    private final PlatformLogger logger;
    private final BooleanSupplier shuttingDown;
    private final Consumer<ConfigKey> onChange;

    ConfigFileWatcher(PlatformLogger logger, BooleanSupplier shuttingDown, Consumer<ConfigKey> onChange) {
        this.logger = logger;
        this.shuttingDown = shuttingDown;
        this.onChange = onChange;
    }

    void register(ConfigEntry<?> entry) {
        if (watchServiceUnavailable) {
            return;
        }
        File file = entry.file;
        Path filePath = file.toPath().toAbsolutePath();
        Path directory = filePath.getParent();
        if (directory == null) {
            directory = filePath;
        }

        fileIndex.computeIfAbsent(filePath, key -> ConcurrentHashMap.newKeySet()).add(entry.key);

        try {
            ensureWatchService();
            synchronized (watcherLock) {
                if (!directoryWatchKeys.containsKey(directory)) {
                    Files.createDirectories(directory);

                    WatchKey watchKey = directory.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    directoryWatchKeys.put(directory, watchKey);
                }

                directoryRefCounts.merge(directory, 1, Integer::sum);
            }
            startWatcherThread();

        } catch (IOException e) {
            watchServiceUnavailable = true;
            if (!watchServiceWarned) {
                watchServiceWarned = true;
                logger.warn("Failed to register config watcher (disabling realtime reload): "
                        + entry.metadata.getFilePath(), e);
            }
        }
    }

    private synchronized void ensureWatchService() throws IOException {
        if (watchService == null) {
            watchService = FileSystems.getDefault().newWatchService();
        }
    }

    private synchronized void startWatcherThread() {
        if (watcherExecutor != null) {
            return;
        }

        watcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MagicUtils-ConfigWatcher");
            thread.setDaemon(true);
            return thread;
        });

        watcherExecutor.submit(this::watchLoop);
    }

    private void watchLoop() {
        while (!shuttingDown.getAsBoolean()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path directory = (Path) key.watchable();
            Set<Path> changedFiles = new HashSet<>();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path changed = directory.resolve((Path) event.context()).toAbsolutePath();
                changedFiles.add(changed);
            }

            for (Path changed : changedFiles) {
                Set<ConfigKey> affectedKeys = fileIndex.get(changed);
                if (affectedKeys == null || affectedKeys.isEmpty()) {
                    continue;
                }

                for (ConfigKey configKey : affectedKeys) {
                    onChange.accept(configKey);
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                synchronized (watcherLock) {
                    directoryWatchKeys.remove(directory);
                    directoryRefCounts.remove(directory);
                }
            }
        }
    }

    void unregister(ConfigEntry<?> entry) {
        Path filePath = entry.file.toPath().toAbsolutePath();
        Set<ConfigKey> fileKeys = fileIndex.get(filePath);
        if (fileKeys != null) {
            fileKeys.remove(entry.key);
            if (fileKeys.isEmpty()) {
                fileIndex.remove(filePath);
            }
        }

        Path dir = filePath.getParent();
        synchronized (watcherLock) {
            directoryRefCounts.computeIfPresent(dir, (path, count) -> {
                int next = count - 1;
                if (next <= 0) {
                    WatchKey watchKey = directoryWatchKeys.remove(path);
                    if (watchKey != null) {
                        watchKey.cancel();
                    }
                    return null;
                }
                return next;
            });
        }
    }

    /**
     * Stops the watcher thread and closes the watch service.
     */
    void close() {
        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
            try {
                watcherExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watcherExecutor = null;
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Failed to close config watch service", e);
            }
            watchService = null;
        }
    }

    void clear() {
        directoryWatchKeys.clear();
        directoryRefCounts.clear();
        fileIndex.clear();
    }
}
