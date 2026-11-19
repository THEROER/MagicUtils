package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.Tasks;
import dev.ua.theroer.magicutils.platform.ThreadContext;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Platform-agnostic configuration manager using annotation-driven mapping and Jackson (YAML/JSON/JSONC/TOML).
 */
public class ConfigManager {
    private final Platform platform;
    private final PlatformLogger logger;
    private final TaskScheduler scheduler;
    private final ConfigFormatResolver formatResolver;
    private final ConfigMigrator migrator;
    private final ConfigFieldMapper fieldMapper;
    private final ConfigFileWatcher watcher;
    private final ShutdownHookRegistrar shutdownRegistrar;
    private final Runnable shutdownHook;
    private final Map<ConfigKey, ConfigEntry<?>> configs = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<ConfigKey>> classIndex = new ConcurrentHashMap<>();
    private final Map<Object, ConfigKey> instanceIndex = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> primaryInstances = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<BiConsumer<Object, Set<String>>>> changeListeners = new ConcurrentHashMap<>();

    private ExecutorService reloadExecutor;
    private volatile boolean shuttingDown = false;
    /**
     * Guards the {@link #shuttingDown} flag against external reload tasks.
     *
     * <p>{@link #shutdown()} sets {@code shuttingDown=true} under this lock,
     * and each watcher-driven reload task acquires the lock before reading
     * the flag. This eliminates the race where a queued task observes the
     * flag still {@code false} while shutdown is mid-flight, then proceeds
     * to mutate config state and notify listeners after the manager is
     * supposed to be quiet.</p>
     */
    private final Object shutdownLock = new Object();

    /**
     * Creates a new ConfigManager for the provided platform.
     *
     * @param platform platform abstraction
     */
    @SuppressWarnings("this-escape")
    public ConfigManager(Platform platform) {
        this.platform = platform;
        this.logger = platform.logger();
        this.scheduler = Tasks.scheduler(platform);
        this.formatResolver = new ConfigFormatResolver(platform, logger);
        this.migrator = new ConfigMigrator(logger);
        this.fieldMapper = new ConfigFieldMapper(platform, logger);
        this.watcher = new ConfigFileWatcher(logger, () -> shuttingDown, this::onWatchedFileChanged);
        ShutdownHookRegistrar registrar = platform instanceof ShutdownHookRegistrar hookRegistrar
                ? hookRegistrar
                : null;
        this.shutdownRegistrar = registrar;
        this.shutdownHook = this::shutdown;
        if (registrar != null) {
            registrar.registerShutdownHook(shutdownHook);
        }
    }

    /**
     * Registers and loads a configuration class.
     *
     * @param configClass the configuration class
     * @param <T>         the type of configuration
     * @return the loaded configuration instance
     */
    public <T> T register(Class<T> configClass) {
        return register(configClass, new HashMap<>());
    }

    /**
     * Registers and loads a configuration class with placeholder replacements.
     *
     * @param configClass  the configuration class
     * @param placeholders placeholder replacements for the file path
     * @param <T>          the type of configuration
     * @return the loaded configuration instance
     */
    public <T> T register(Class<T> configClass, Map<String, String> placeholders) {
        try {
            ConfigFile configFile = configClass.getAnnotation(ConfigFile.class);
            if (configFile == null) {
                throw new IllegalArgumentException("Missing @ConfigFile on " + configClass.getName());
            }

            Map<String, String> placeholdersCopy = placeholders == null ? new HashMap<>() : new HashMap<>(placeholders);
            ConfigFormatResolver.FormatDecision formatDecision =
                    formatResolver.resolveFormatDecision(configFile, placeholdersCopy);
            ConfigMetadata metadata = new ConfigMetadata(formatDecision.targetPath(), configFile.autoCreate(),
                    configFile.template(), platform.configDir());
            ConfigKey key = new ConfigKey(configClass, metadata.getFilePath());

            @SuppressWarnings("unchecked")
            ConfigEntry<T> existingEntry = (ConfigEntry<T>) configs.get(key);
            if (existingEntry != null) {
                return existingEntry.instance;
            }

            T instance = configClass.getDeclaredConstructor().newInstance();
            LoadResult loadResult;
            if (formatDecision.sourcePath() != null) {
                ConfigMetadata sourceMetadata = new ConfigMetadata(formatDecision.sourcePath(), configFile.autoCreate(),
                        configFile.template(), platform.configDir());
                loadResult = loadConfig(instance, sourceMetadata);
                saveConfigToFile(instance, metadata, loadResult.schemaVersion());
                logger.info("Migrated config " + sourceMetadata.getFilePath()
                        + " -> " + metadata.getFilePath());
            } else {
                loadResult = loadConfig(instance, metadata);
            }

            ConfigEntry<T> entry = new ConfigEntry<>(key, instance, metadata,
                    platform.configDir().resolve(metadata.getFilePath()).toFile());
            entry.schemaVersion = loadResult.schemaVersion();
            entry.refreshLastModified();

            configs.put(key, entry);
            instanceIndex.put(instance, key);
            classIndex.computeIfAbsent(configClass, clazz -> ConcurrentHashMap.newKeySet()).add(key);
            primaryInstances.putIfAbsent(configClass, instance);

            watcher.register(entry);
            return instance;

        } catch (Exception e) {
            logger.error("Failed to register config: " + configClass.getName(), e);
            throw new RuntimeException("Failed to register config", e);
        }
    }

    /**
     * Registers migrations for a config class.
     *
     * @param configClass config type to associate with migrations
     * @param migrations migration steps
     */
    public void registerMigrations(Class<?> configClass, ConfigMigration... migrations) {
        if (configClass == null || migrations == null) {
            return;
        }
        for (ConfigMigration migration : migrations) {
            migrator.register(configClass, migration);
        }
    }

    /**
     * Registers a single migration for a config class.
     *
     * @param configClass config type to associate with migration
     * @param migration migration step
     */
    public void registerMigration(Class<?> configClass, ConfigMigration migration) {
        if (configClass == null || migration == null) {
            return;
        }
        migrator.register(configClass, migration);
    }

    /**
     * Registers and loads a configuration class using placeholder pairs.
     * Accepts {@code key, value, key, value...} and converts them to a map.
     *
     * @param configClass the configuration class
     * @param placeholders key/value pairs for placeholders
     * @param <T> the type of configuration
     * @return loaded configuration instance
     */
    public <T> T loadConfigFile(Class<T> configClass, String... placeholders) {
        return register(configClass, ConfigFormatResolver.toPlaceholderMap(placeholders));
    }

    /**
     * Saves all registered instances of the given config class.
     *
     * @param <T> config type
     * @param configClass config type to save
     */
    public <T> void saveConfigFile(Class<T> configClass) {
        save(configClass);
    }

    /**
     * Saves a specific configuration instance.
     *
     * @param configInstance instance to persist
     */
    public void saveConfigFile(Object configInstance) {
        save(configInstance);
    }

    /**
     * Reloads a configuration class with optional sections.
     *
     * @param <T> config type
     * @param configClass config type to reload
     * @param sections optional sections
     */
    public <T> void reloadConfigFile(Class<T> configClass, String... sections) {
        reload(configClass, sections);
    }

    /**
     * Reloads a specific configuration instance.
     *
     * @param configInstance instance to reload
     */
    public void reloadConfigFile(Object configInstance) {
        reload(configInstance);
    }

    /**
     * Reload all registered configuration instances from disk.
     */
    public void reloadAll() {
        warnIfMainThread("reloadAll");
        List<ConfigEntry<?>> entries = new ArrayList<>(configs.values());
        for (ConfigEntry<?> entry : entries) {
                if (reloadEntry(entry, Collections.emptySet(), false) && shouldNotifyOnChange(entry)) {
                    notifyChangeListeners(entry, Collections.emptySet());
                }
            }
        }

    /**
     * Unregisters all instances of a config class.
     *
     * @param configClass config type to unload
     */
    public void unloadConfigFile(Class<?> configClass) {
        for (ConfigEntry<?> entry : new ArrayList<>(getEntries(configClass))) {
            removeEntry(entry);
        }
    }

    private <T> LoadResult loadConfig(T instance, ConfigMetadata metadata) throws Exception {
        return loadConfig(instance, metadata, Collections.emptySet());
    }

    /**
     * Loads a config with one retry on ConcurrentModificationException.
     * <p>
     * If a user-supplied Map/List in the config instance throws CME during the first
     * iteration (a transient state, e.g. another thread mutating it), retry once —
     * the second attempt typically succeeds because the new document load replaces
     * the field values before iteration is needed again.
     */
    private <T> LoadResult loadConfigWithRetry(T instance, ConfigMetadata metadata, Set<String> sections) throws Exception {
        try {
            return loadConfig(instance, metadata, sections);
        } catch (java.util.ConcurrentModificationException firstCme) {
            try {
                return loadConfig(instance, metadata, sections);
            } catch (java.util.ConcurrentModificationException secondCme) {
                secondCme.addSuppressed(firstCme);
                throw secondCme;
            }
        }
    }

    private <T> LoadResult loadConfig(T instance, ConfigMetadata metadata, Set<String> sections) throws Exception {
        File configFile = metadata.resolveFile(platform.configDir());
        boolean created = false;

        if (!configFile.exists() && metadata.isAutoCreate()) {
            createDefaultConfig(instance, configFile, metadata);
            created = true;
        }

        ConfigDocument document = ConfigDocument.load(configFile);
        ConfigMigrator.MigrationResult migrationResult = migrator.applyMigrations(instance.getClass(), document, created);
        Set<String> normalizedSections = fieldMapper.normalizeSections(sections);
        boolean fullReload = normalizedSections.isEmpty() || migrationResult.shouldSave();
        fieldMapper.processFields(instance, document, instance.getClass(), "",
                fullReload ? Collections.emptySet() : normalizedSections);
        if (migrationResult.shouldSave()) {
            saveConfigToFile(instance, metadata, migrationResult.schemaVersion());
        }
        return new LoadResult(migrationResult.schemaVersion(), migrationResult.migrated());
    }

    private <T> void createDefaultConfig(T instance, File configFile, ConfigMetadata metadata) throws Exception {
        ConfigDocument.ensureParentDirectory(configFile);

        ConfigDocument document = ConfigDocument.empty();

        Comment classComment = instance.getClass().getAnnotation(Comment.class);
        if (classComment != null) {
            document.setHeader(Arrays.asList(classComment.value().split("\n")));
        }

        fieldMapper.writeDefaults(instance, document, instance.getClass(), "");
        document.save(configFile);
        fieldMapper.flushExternalFields(instance, instance.getClass(), "");
        logger.info("Created default config: " + configFile.getName());
    }

    /**
     * Saves all registered instances of the given config class.
     *
     * @param <T> config type
     * @param configClass config type to save
     */
    public <T> void save(Class<T> configClass) {
        for (ConfigEntry<?> entry : getEntries(configClass)) {
            saveEntry(entry);
        }
    }

    /**
     * Saves the provided configuration instance.
     *
     * @param configInstance config instance to persist
     */
    public void save(Object configInstance) {
        ConfigEntry<?> entry = getEntry(configInstance);
        if (entry != null) {
            saveEntry(entry);
        }
    }

    /**
     * Reloads the provided configuration instance from disk.
     *
     * @param configInstance config instance to reload
     */
    public void reload(Object configInstance) {
        warnIfMainThread("reload");
        ConfigEntry<?> entry = getEntry(configInstance);
        if (entry == null) {
            return;
        }

        if (reloadEntry(entry, Collections.emptySet(), false)) {
            ConfigReloadable reloadable = entry.key.configClass.getAnnotation(ConfigReloadable.class);
            if (reloadable != null && reloadable.notifyOnChange()) {
                notifyChangeListeners(entry, Collections.emptySet());
            }
        }
    }

    /**
     * Alias for {@link #reload(Object)}.
     *
     * @param configInstance config instance to reload
     */
    public void mergeFromFile(Object configInstance) {
        reload(configInstance);
    }

    /**
     * Alias for {@link #save(Object)}.
     *
     * @param configInstance config instance to save
     */
    public void mergeToFile(Object configInstance) {
        save(configInstance);
    }

    /**
     * Unregisters the configuration and stops tracking file changes.
     *
     * @param configInstance config instance to remove
     */
    public void unload(Object configInstance) {
        ConfigEntry<?> entry = getEntry(configInstance);
        if (entry != null) {
            removeEntry(entry);
        }
    }

    private void saveConfigToFile(Object instance, ConfigMetadata metadata, String schemaVersion) throws Exception {
        ConfigDocument document = ConfigDocument.empty();
        fieldMapper.saveFields(instance, document, instance.getClass(), "");
        migrator.applySchemaVersion(document, schemaVersion);
        File file = metadata.resolveFile(platform.configDir());
        ConfigDocument.ensureParentDirectory(file);
        document.save(file);
        fieldMapper.flushExternalFields(instance, instance.getClass(), "");
    }

    /**
     * Retrieves the primary registered instance of the given config class.
     *
     * @param <T> config type
     * @param configClass config type to fetch
     * @return primary instance or null if none registered
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<T> configClass) {
        return (T) primaryInstances.get(configClass);
    }

    /**
     * Reloads all registered instances of a config class from disk.
     *
     * @param <T> config type
     * @param configClass config type to reload
     */
    public <T> void reload(Class<T> configClass) {
        reload(configClass, new String[0]);
    }

    /**
     * Reloads registered instances of a config class, optionally scoping to specific sections.
     *
     * @param <T> config type
     * @param configClass config type to reload
     * @param sections sections to reload (empty for full reload)
     */
    public <T> void reload(Class<T> configClass, String... sections) {
        warnIfMainThread("reload");
        Set<String> sectionSet = (sections == null || sections.length == 0)
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(sections));

        ConfigReloadable reloadable = configClass.getAnnotation(ConfigReloadable.class);
        if (reloadable != null && reloadable.sections().length > 0 && !sectionSet.isEmpty()) {
            Set<String> allowedSections = new HashSet<>(Arrays.asList(reloadable.sections()));
            for (String section : sectionSet) {
                if (!allowedSections.contains(section)) {
                    logger.warn("Section '" + section + "' is not reloadable for " + configClass.getSimpleName());
                    return;
                }
            }
        }

        for (ConfigEntry<?> entry : getEntries(configClass)) {
            if (reloadEntry(entry, sectionSet, false) && shouldNotifyOnChange(entry)) {
                notifyChangeListeners(entry, sectionSet);
            }
        }
    }

    /**
     * Reloads a configuration instance asynchronously.
     *
     * @param configInstance config instance to reload
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAsync(Object configInstance) {
        return CompletableFuture.runAsync(() -> reload(configInstance), scheduler.io());
    }

    /**
     * Reloads a configuration instance, auto-switching to async on sensitive threads.
     *
     * @param configInstance config instance
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadSmart(Object configInstance) {
        if (isBlockingSensitiveThread()) {
            return reloadAsync(configInstance);
        }
        reload(configInstance);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Reloads config instances for a class asynchronously.
     *
     * @param configClass config type
     * @param sections optional sections
     * @param <T> config type
     * @return future that completes after reload
     */
    public <T> CompletableFuture<Void> reloadAsync(Class<T> configClass, String... sections) {
        return CompletableFuture.runAsync(() -> reload(configClass, sections), scheduler.io());
    }

    /**
     * Reloads config instances for a class, auto-switching to async on sensitive threads.
     *
     * @param configClass config class
     * @param sections optional sections
     * @param <T> config type
     * @return future that completes after reload
     */
    public <T> CompletableFuture<Void> reloadSmart(Class<T> configClass, String... sections) {
        if (isBlockingSensitiveThread()) {
            return reloadAsync(configClass, sections);
        }
        reload(configClass, sections);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Reloads all registered configs asynchronously.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAllAsync() {
        return CompletableFuture.runAsync(this::reloadAll, scheduler.io());
    }

    /**
     * Reloads all registered configs, auto-switching to async on sensitive threads.
     *
     * @return future that completes after reload
     */
    public CompletableFuture<Void> reloadAllSmart() {
        if (isBlockingSensitiveThread()) {
            return reloadAllAsync();
        }
        reloadAll();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Registers a listener that fires when the config changes on disk.
     *
     * @param <T> config type
     * @param configClass config type to monitor
     * @param listener callback receiving updated instance and changed sections
     */
    public <T> void onChange(Class<T> configClass, BiConsumer<T, Set<String>> listener) {
        subscribeChanges(configClass, listener);
    }

    /**
     * Subscribes to config change notifications and returns an unregister handle.
     *
     * @param <T> config type
     * @param configClass config type to monitor
     * @param listener callback receiving updated instance and changed sections
     * @return subscription handle that removes the listener when closed
     */
    @SuppressWarnings("unchecked")
    public <T> ListenerSubscription subscribeChanges(Class<T> configClass, BiConsumer<T, Set<String>> listener) {
        Objects.requireNonNull(configClass, "configClass");
        Objects.requireNonNull(listener, "listener");

        CopyOnWriteArrayList<BiConsumer<Object, Set<String>>> listeners =
                (CopyOnWriteArrayList<BiConsumer<Object, Set<String>>>) changeListeners.computeIfAbsent(
                        configClass, k -> new CopyOnWriteArrayList<>());
        BiConsumer<Object, Set<String>> typedListener = (BiConsumer<Object, Set<String>>) listener;
        listeners.add(typedListener);
        return () -> {
            listeners.remove(typedListener);
            if (listeners.isEmpty()) {
                changeListeners.remove(configClass, listeners);
            }
        };
    }

    private void notifyChangeListeners(ConfigEntry<?> entry, Set<String> changedSections) {
        List<BiConsumer<Object, Set<String>>> listeners = changeListeners.get(entry.key.configClass);
        if (listeners == null) {
            return;
        }

        for (BiConsumer<Object, Set<String>> listener : listeners) {
            try {
                listener.accept(entry.instance, changedSections);
            } catch (Throwable error) {
                logger.error("Failed to notify config change listener for "
                        + entry.key.configClass.getName(), error);
            }
        }
    }

    private boolean shouldNotifyOnChange(ConfigEntry<?> entry) {
        ConfigReloadable reloadable = entry.key.configClass.getAnnotation(ConfigReloadable.class);
        return reloadable == null || reloadable.notifyOnChange();
    }

    private Collection<ConfigEntry<?>> getEntries(Class<?> configClass) {
        Set<ConfigKey> keys = classIndex.get(configClass);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConfigEntry<?>> result = new ArrayList<>(keys.size());
        for (ConfigKey key : keys) {
            ConfigEntry<?> entry = configs.get(key);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private ConfigEntry<?> getEntry(Object configInstance) {
        if (configInstance == null) {
            return null;
        }

        ConfigKey key = instanceIndex.get(configInstance);
        return key != null ? configs.get(key) : null;
    }

    private void removeEntry(ConfigEntry<?> entry) {
        configs.remove(entry.key);
        instanceIndex.remove(entry.instance);

        Set<ConfigKey> keys = classIndex.get(entry.key.configClass);
        if (keys != null) {
            keys.remove(entry.key);
            if (keys.isEmpty()) {
                classIndex.remove(entry.key.configClass);
                if (primaryInstances.get(entry.key.configClass) == entry.instance) {
                    primaryInstances.remove(entry.key.configClass);
                }
            } else if (primaryInstances.get(entry.key.configClass) == entry.instance) {
                ConfigEntry<?> nextEntry = configs.get(keys.iterator().next());
                if (nextEntry != null) {
                    primaryInstances.put(entry.key.configClass, nextEntry.instance);
                } else {
                    primaryInstances.remove(entry.key.configClass);
                }
            }
        }

        watcher.unregister(entry);
    }

    private void saveEntry(ConfigEntry<?> entry) {
        synchronized (entry.monitor) {
            try {
                long currentFileModified = entry.file.exists() ? entry.file.lastModified() : -1L;
                long lastKnownModified = entry.getLastModified();

                if (entry.file.exists() && currentFileModified > lastKnownModified) {
                    reloadEntry(entry, Collections.emptySet(), true);
                    entry.refreshLastModified();
                }

                ConfigDocument document = ConfigDocument.empty();
                fieldMapper.saveFields(entry.instance, document, entry.instance.getClass(), "");
                migrator.applySchemaVersion(document, entry.schemaVersion);

                ConfigDocument.ensureParentDirectory(entry.file);
                document.save(entry.file);
                fieldMapper.flushExternalFields(entry.instance, entry.instance.getClass(), "");
                entry.refreshLastModified();
            } catch (Exception e) {
            logger.error("Failed to save config " + entry.key.configClass.getName() + " (" + entry.metadata.getFilePath()
                    + ")", e);
            }
        }
    }

    private boolean reloadEntry(ConfigEntry<?> entry, Set<String> sections, boolean externalTrigger) {
        synchronized (entry.monitor) {
            try {
                long beforeReload = entry.file.exists() ? entry.file.lastModified() : -1L;
                // Always mutate in-place: the instance reference returned from register()
                // is part of the public contract — consumers hold it and expect getter
                // calls to see live data without re-fetching.
                LoadResult loadResult = loadConfigWithRetry(entry.instance, entry.metadata, sections);
                entry.schemaVersion = loadResult.schemaVersion();

                entry.lastModified.set(beforeReload);
                entry.refreshLastModified();
                return true;
            } catch (Exception e) {
                String reason = externalTrigger ? "external" : "internal";
                logger.error("Failed to reload config " + entry.key.configClass.getName() + " (" + entry.metadata.getFilePath()
                        + ") via " + reason, e);
                return false;
            }
        }
    }

    private void onWatchedFileChanged(ConfigKey configKey) {
        ConfigEntry<?> entry = configs.get(configKey);
        if (entry != null) {
            scheduleExternalReload(entry);
        }
    }

    private void scheduleExternalReload(ConfigEntry<?> entry) {
        if (shuttingDown) {
            return;
        }

        if (!entry.markReloadScheduled()) {
            return;
        }

        ensureReloadExecutor();
        reloadExecutor.submit(() -> {
            try {
                synchronized (shutdownLock) {
                    if (shuttingDown) {
                        return;
                    }
                    boolean reloaded;
                    // The mtime check runs under entry.monitor, the lock saveEntry
                    // holds from writing the file until it records the new mtime.
                    // Checked outside it, an echo of our own save could be sampled
                    // mid-save (file written, mtime not yet recorded), look external,
                    // and then reload stale disk state over in-memory changes made
                    // after that save (e.g. a custom message about to be saved).
                    synchronized (entry.monitor) {
                        // Skip reload if the file's mtime matches what we last
                        // saw — this drops spurious watcher events caused by
                        // initial registration writes (saveConfig defaults), and
                        // duplicate ENTRY_CREATE/ENTRY_MODIFY pairs the OS emits
                        // for the same physical save.
                        long observed = entry.file.exists() ? entry.file.lastModified() : -1L;
                        long lastKnown = entry.lastModified.get();
                        if (lastKnown > 0 && observed == lastKnown) {
                            return;
                        }
                        reloaded = reloadEntry(entry, Collections.emptySet(), true);
                    }
                    if (reloaded) {
                        notifyChangeListenersOnMain(entry, Collections.emptySet());
                    }
                }
            } finally {
                entry.reloadComplete();
            }
        });
    }

    private void notifyChangeListenersOnMain(ConfigEntry<?> entry, Set<String> sections) {
        Tasks.runOnMain(platform, () -> notifyChangeListeners(entry, sections)).whenComplete((ignored, error) -> {
            if (error != null) {
                logger.warn("Failed to notify config change listeners for " + entry.key.filePath, error);
            }
        });
    }

    private void warnIfMainThread(String action) {
        if (isBlockingSensitiveThread()) {
            logger.warn("ConfigManager." + action + " performs disk I/O. Consider using " + action
                    + "Async() to avoid main-thread stalls.");
        }
    }

    private boolean isBlockingSensitiveThread() {
        if (platform == null) {
            return false;
        }
        ThreadContext context = platform.threadContext();
        if (context == ThreadContext.UNKNOWN) {
            return platform.isMainThread();
        }
        return context.isBlockingSensitive();
    }

    /**
     * Stops the watcher service and releases associated resources.
     */
    public void shutdown() {
        synchronized (shutdownLock) {
            shuttingDown = true;
        }
        if (shutdownRegistrar != null && shutdownHook != null) {
            shutdownRegistrar.unregisterShutdownHook(shutdownHook);
        }

        // Stop the watcher first so no new external reloads are queued while
        // we drain the reload executor. Closing watchService also unblocks
        // watchService.take() inside watchLoop.
        watcher.close();

        // Drain reload executor gracefully — let in-flight tasks observe
        // shuttingDown under shutdownLock and skip their work, then exit.
        // We avoid shutdownNow() here so currently-running reloadEntry()
        // calls can release entry.monitor cleanly.
        if (reloadExecutor != null) {
            reloadExecutor.shutdown();
            try {
                if (!reloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reloadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reloadExecutor.shutdownNow();
            }
            reloadExecutor = null;
        }

        watcher.clear();
    }

    private synchronized void ensureReloadExecutor() {
        if (reloadExecutor != null) {
            return;
        }
        reloadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MagicUtils-ConfigReload");
            thread.setDaemon(true);
            return thread;
        });
    }

    private record LoadResult(String schemaVersion, boolean migrated) {
    }

}
