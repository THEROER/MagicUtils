package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.ConfigSerializable;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSerializerTest {

    @Test
    void serializesAndDeserializesInheritedFields() {
        ChildConfig original = new ChildConfig();
        original.base = "base-updated";
        original.child = "child-updated";

        Map<String, Object> serialized = ConfigSerializer.serialize(original);
        ChildConfig restored = ConfigSerializer.deserialize(TestLogger.INSTANCE, serialized, ChildConfig.class);

        assertEquals("base-updated", serialized.get("base"));
        assertEquals("child-updated", serialized.get("child"));
        assertEquals("base-updated", restored.base);
        assertEquals("child-updated", restored.child);
    }

    @Test
    void keepsDefaultsAndLogsWarningForInvalidValues() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", "not-a-number");
        data.put("enabled", null);

        TestLogger logger = new TestLogger();
        PrimitiveConfig restored = ConfigSerializer.deserialize(logger, data, PrimitiveConfig.class);

        assertEquals(7, restored.count);
        assertTrue(restored.enabled);
        assertEquals(1, logger.warnings.size());
        assertTrue(logger.warnings.get(0).contains("count"));
    }

    @ConfigSerializable
    static class BaseConfig {
        @ConfigValue("base")
        String base = "base-default";
    }

    @ConfigSerializable
    static final class ChildConfig extends BaseConfig {
        @ConfigValue("child")
        String child = "child-default";
    }

    @ConfigSerializable
    static final class PrimitiveConfig {
        @ConfigValue("count")
        int count = 7;

        @ConfigValue("enabled")
        boolean enabled = true;
    }

    private static final class TestLogger implements PlatformLogger {
        private static final TestLogger INSTANCE = new TestLogger();

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            warnings.add(message);
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }
}
