package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds registered {@link ConfigMigration} chains per config class and applies them
 * to loaded documents, tracking the {@code config-version} schema marker.
 */
final class ConfigMigrator {
    private static final String MIGRATION_VERSION_KEY = "config-version";

    private final Map<Class<?>, MigrationChain> migrationChains = new ConcurrentHashMap<>();
    private final PlatformLogger logger;

    ConfigMigrator(PlatformLogger logger) {
        this.logger = logger;
    }

    void register(Class<?> configClass, ConfigMigration migration) {
        MigrationChain chain = migrationChains.computeIfAbsent(configClass, k -> new MigrationChain());
        chain.register(migration, logger);
    }

    MigrationResult applyMigrations(Class<?> configClass, ConfigDocument document, boolean createdNew) {
        MigrationChain chain = migrationChains.get(configClass);
        if (chain == null || chain.isEmpty()) {
            return new MigrationResult(null, false, false);
        }
        Map<String, Object> data = document.data;
        String rawVersion = normalizeVersion(data.get(MIGRATION_VERSION_KEY));
        boolean versionMissing = rawVersion == null;
        String currentVersion = versionMissing ? "0" : rawVersion;
        boolean migrated = false;
        boolean changed = false;
        Set<String> visited = new HashSet<>();

        while (true) {
            if (!visited.add(currentVersion)) {
                logger.warn("Config migration cycle detected for " + configClass.getSimpleName()
                        + " at version " + currentVersion);
                break;
            }
            ConfigMigration migration = chain.byFrom.get(currentVersion);
            if (migration == null) {
                break;
            }
            migration.migrate(data);
            String nextVersion = normalizeVersion(migration.toVersion());
            if (nextVersion == null) {
                logger.warn("Migration from " + currentVersion + " returned empty target version for "
                        + configClass.getSimpleName());
                break;
            }
            currentVersion = nextVersion;
            migrated = true;
            changed = true;
        }

        if (migrated) {
            data.put(MIGRATION_VERSION_KEY, currentVersion);
        } else if (versionMissing && createdNew && chain.latestVersion != null) {
            currentVersion = chain.latestVersion;
            data.put(MIGRATION_VERSION_KEY, currentVersion);
            changed = true;
        } else if (versionMissing && !createdNew && chain.latestVersion != null) {
            logger.warn("Config " + configClass.getSimpleName() + " is missing " + MIGRATION_VERSION_KEY
                    + ". Add a migration from version 0 or set the version explicitly.");
        } else if (rawVersion != null && chain.latestVersion != null && !rawVersion.equals(chain.latestVersion)) {
            logger.warn("Config " + configClass.getSimpleName() + " is at version " + rawVersion
                    + " but latest is " + chain.latestVersion + ". Missing migration?");
        }

        return new MigrationResult(currentVersion, changed, migrated);
    }

    void applySchemaVersion(ConfigDocument document, String schemaVersion) {
        if (document == null) {
            return;
        }
        String normalized = normalizeVersion(schemaVersion);
        if (normalized == null) {
            return;
        }
        document.set(MIGRATION_VERSION_KEY, normalized, null);
    }

    private static String normalizeVersion(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            long asLong = number.longValue();
            double asDouble = number.doubleValue();
            if (asDouble == (double) asLong) {
                return String.valueOf(asLong);
            }
            return String.valueOf(asDouble);
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    record MigrationResult(String schemaVersion, boolean shouldSave, boolean migrated) {
    }

    private static final class MigrationChain {
        private final Map<String, ConfigMigration> byFrom = new LinkedHashMap<>();
        private String latestVersion;

        private boolean isEmpty() {
            return byFrom.isEmpty();
        }

        private void register(ConfigMigration migration, PlatformLogger logger) {
            if (migration == null) {
                return;
            }
            String from = normalizeVersion(migration.fromVersion());
            String to = normalizeVersion(migration.toVersion());
            if (from == null || to == null) {
                if (logger != null) {
                    logger.warn("Skipping migration with empty version identifiers: " + migration.getClass().getName());
                }
                return;
            }
            if (byFrom.containsKey(from) && logger != null) {
                logger.warn("Replacing migration for version " + from + " with " + migration.getClass().getName());
            }
            byFrom.put(from, migration);
            recomputeLatest(logger);
        }

        private void recomputeLatest(PlatformLogger logger) {
            if (byFrom.isEmpty()) {
                latestVersion = null;
                return;
            }
            Set<String> fromVersions = new LinkedHashSet<>(byFrom.keySet());
            Set<String> toVersions = new LinkedHashSet<>();
            for (ConfigMigration migration : byFrom.values()) {
                String to = normalizeVersion(migration.toVersion());
                if (to != null) {
                    toVersions.add(to);
                }
            }
            Set<String> endCandidates = new LinkedHashSet<>(toVersions);
            endCandidates.removeAll(fromVersions);
            if (endCandidates.size() == 1) {
                latestVersion = endCandidates.iterator().next();
                return;
            }
            if (!toVersions.isEmpty()) {
                latestVersion = new ArrayList<>(toVersions).get(toVersions.size() - 1);
                if (logger != null && endCandidates.size() > 1) {
                    logger.warn("Multiple migration endpoints detected. Using version " + latestVersion);
                }
                return;
            }
            latestVersion = null;
        }
    }
}
