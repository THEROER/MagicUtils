package dev.ua.theroer.magicutils.config;

import java.util.Map;

/**
 * Defines a migration step for configuration data.
 * Implementations should mutate the provided root map in-place.
 */
public interface ConfigMigration {
    /**
     * Source version identifier for this migration.
     *
     * @return source version identifier
     */
    String fromVersion();

    /**
     * Target version identifier for this migration.
     *
     * @return target version identifier
     */
    String toVersion();

    /**
     * Applies migration changes to the raw config map.
     *
     * @param root root config map
     */
    void migrate(Map<String, Object> root);
}
