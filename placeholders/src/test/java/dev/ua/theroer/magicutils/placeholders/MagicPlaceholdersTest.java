package dev.ua.theroer.magicutils.placeholders;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicPlaceholdersTest {

    @AfterEach
    void cleanupRegistry() {
        MagicPlaceholders.clearAll();
    }

    @Test
    void clearAllRemovesRegisteredStateAndDetachesListeners() {
        AtomicInteger registeredEvents = new AtomicInteger();
        AtomicInteger debugEvents = new AtomicInteger();

        MagicPlaceholders.PlaceholderListener listener = new MagicPlaceholders.PlaceholderListener() {
            @Override
            public void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key) {
                registeredEvents.incrementAndGet();
            }

            @Override
            public void onPlaceholderUnregistered(MagicPlaceholders.PlaceholderKey key) {
            }

            @Override
            public void onNamespaceUpdated(String namespace) {
            }
        };
        MagicPlaceholders.PlaceholderDebugListener debugListener =
                (key, ownerKey, audience, argument, value, error) -> debugEvents.incrementAndGet();

        MagicPlaceholders.addListener(listener);
        MagicPlaceholders.addDebugListener(debugListener);
        MagicPlaceholders.registerNamespace(" Demo ", "Alice", "1.0.0");
        MagicPlaceholders.register(" Demo ", "User", (audience, argument) -> "ns-" + argument);

        assertEquals(1, registeredEvents.get());
        assertEquals(Set.of("demo"), MagicPlaceholders.namespaces());
        assertEquals("Alice", MagicPlaceholders.getNamespaceMeta("demo").author());
        assertEquals("1.0.0", MagicPlaceholders.getNamespaceMeta("demo").version());
        assertEquals("ns-value", MagicPlaceholders.render(PlaceholderContext.builder().build(), "{demo:user:value}"));
        assertEquals(1, debugEvents.get());

        MagicPlaceholders.clearAll();

        assertTrue(MagicPlaceholders.entries().isEmpty());
        assertTrue(MagicPlaceholders.namespaces().isEmpty());
        assertEquals("{demo:user:value}",
                MagicPlaceholders.render(PlaceholderContext.builder().build(), "{demo:user:value}"));

        MagicPlaceholders.register("demo", "again", (audience, argument) -> "value");
        assertEquals("value", MagicPlaceholders.resolve("demo", "again", null, null));
        assertEquals(1, registeredEvents.get());
        assertEquals(1, debugEvents.get());
    }

    @Test
    void clearLocalFallsBackToNamespacedThenGlobalResolution() {
        Object owner = new Object();
        PlaceholderContext context = PlaceholderContext.builder()
                .ownerKey(owner)
                .defaultNamespace("demo")
                .build();

        MagicPlaceholders.register("demo", "user", (audience, argument) -> "namespaced");
        MagicPlaceholders.registerGlobal("user", (audience, argument) -> "global");
        MagicPlaceholders.registerLocal(owner, "user", (audience, argument) -> "local");

        assertEquals("local", MagicPlaceholders.render(context, "{user}"));

        MagicPlaceholders.clearLocal(owner);
        assertEquals("namespaced", MagicPlaceholders.render(context, "{user}"));

        MagicPlaceholders.unregister("demo", "user");
        assertEquals("global", MagicPlaceholders.render(context, "{user}"));
    }
}
