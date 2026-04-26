package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.serialization.ConfigAdapters;
import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAdaptersTest {

    @Test
    void registerStoresCustomAdapterAndViewIsUnmodifiable() {
        ConfigValueAdapter<TestValue> adapter = new ConfigValueAdapter<>() {
            @Override
            public TestValue deserialize(Object value) {
                return value == null ? null : new TestValue(String.valueOf(value));
            }

            @Override
            public Object serialize(TestValue value) {
                return value != null ? value.value() : null;
            }
        };

        ConfigAdapters.register(TestValue.class, adapter);

        assertSame(adapter, ConfigAdapters.get(TestValue.class));
        assertTrue(ConfigAdapters.has(TestValue.class));
        assertThrows(UnsupportedOperationException.class, () ->
                ConfigAdapters.getAdaptersView().put(String.class, adapter));
    }

    @Test
    void enumAdaptersAreCreatedLazilyAndCached() {
        assertTrue(!ConfigAdapters.has(TestEnum.class));

        ConfigValueAdapter<TestEnum> first = ConfigAdapters.get(TestEnum.class);
        ConfigValueAdapter<TestEnum> second = ConfigAdapters.get(TestEnum.class);

        assertNotNull(first);
        assertSame(first, second);
        assertTrue(ConfigAdapters.has(TestEnum.class));
        assertEquals(TestEnum.ALPHA, first.deserialize("alpha"));
        assertEquals("BETA", first.serialize(TestEnum.BETA));
    }

    private record TestValue(String value) {
    }

    private enum TestEnum {
        ALPHA,
        BETA
    }
}
