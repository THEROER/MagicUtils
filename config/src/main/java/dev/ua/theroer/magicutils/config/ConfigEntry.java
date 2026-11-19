package dev.ua.theroer.magicutils.config;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A registered config instance with its file, schema version and reload bookkeeping.
 *
 * @param <T> config type
 */
final class ConfigEntry<T> {
    final ConfigKey key;
    final T instance;
    final ConfigMetadata metadata;
    final File file;
    String schemaVersion;
    final Object monitor = new Object();
    private final AtomicBoolean reloadScheduled = new AtomicBoolean(false);
    final AtomicLong lastModified = new AtomicLong(-1L);

    ConfigEntry(ConfigKey key, T instance, ConfigMetadata metadata, File file) {
        this.key = key;
        this.instance = instance;
        this.metadata = metadata;
        this.file = file;
    }

    boolean markReloadScheduled() {
        return reloadScheduled.compareAndSet(false, true);
    }

    void reloadComplete() {
        reloadScheduled.set(false);
    }

    void refreshLastModified() {
        long modified = file.exists() ? file.lastModified() : -1L;
        this.lastModified.set(modified);
    }

    long getLastModified() {
        return this.lastModified.get();
    }
}
