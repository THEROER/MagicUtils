package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private final List<String> warnings = new ArrayList<>();
    private final ConfigMigrator migrator = new ConfigMigrator(new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warn(String message) { warnings.add(message); }
        @Override public void warn(String message, Throwable throwable) { warnings.add(message); }
        @Override public void error(String message) { }
        @Override public void error(String message, Throwable throwable) { }
        @Override public void debug(String message) { }
    });

    private static ConfigMigration step(String from, String to, String key) {
        return new ConfigMigration() {
            @Override public String fromVersion() { return from; }
            @Override public String toVersion() { return to; }
            @Override public void migrate(Map<String, Object> root) { root.put(key, true); }
        };
    }

    @Test
    void appliesChainFromMissingVersion() {
        migrator.register(Object.class, step("0", "1", "a"));
        migrator.register(Object.class, step("1", "2", "b"));
        ConfigDocument document = ConfigDocument.empty();

        ConfigMigrator.MigrationResult result = migrator.applyMigrations(Object.class, document, false);

        assertTrue(result.migrated());
        assertTrue(result.shouldSave());
        assertEquals("2", result.schemaVersion());
        assertEquals("2", document.get("config-version"));
        assertEquals(true, document.get("a"));
        assertEquals(true, document.get("b"));
    }

    @Test
    void newFileIsStampedWithLatestVersionWithoutRunningMigrations() {
        migrator.register(Object.class, step("1", "2", "b"));
        ConfigDocument document = ConfigDocument.empty();

        ConfigMigrator.MigrationResult result = migrator.applyMigrations(Object.class, document, true);

        assertFalse(result.migrated());
        assertTrue(result.shouldSave());
        assertEquals("2", document.get("config-version"));
        assertNull(document.get("b"));
    }

    @Test
    void noChainLeavesDocumentUntouched() {
        ConfigMigrator.MigrationResult result = migrator.applyMigrations(String.class, ConfigDocument.empty(), false);
        assertNull(result.schemaVersion());
        assertFalse(result.shouldSave());
        assertTrue(warnings.isEmpty());
    }
}
