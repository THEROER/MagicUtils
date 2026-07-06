package dev.ua.theroer.magicutils.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MagicUtilsConsumerRegistryTest {

    private static final String MOD = "TestMod";

    @AfterEach
    void cleanup() {
        MagicUtilsConsumerRegistry.unregister(MOD);
    }

    private static MagicUtilsConsumerRegistry.StaticMeta meta() {
        return new MagicUtilsConsumerRegistry.StaticMeta(
                MOD, "1.0.0", "test.Main", null, null, List.of(),
                "Fabric", true, "testmod", Instant.now());
    }

    /** The registry rebuilds the info from the live view on every read, so a
     * change in the view (e.g. commands registered after startup) is reflected
     * without re-registering. This is the bug that showed commands=0. */
    @Test
    void snapshotReflectsLiveViewChanges() {
        AtomicInteger rootCommands = new AtomicInteger(0);
        AtomicInteger typed = new AtomicInteger(0);
        MagicUtilsConsumerRegistry.register(meta(), view(rootCommands, typed, false));

        MagicUtilsConsumerInfo before = single();
        assertEquals(0, before.rootCommandCount());
        assertEquals(0, before.typedComponentCount());

        // Simulate the consumer registering its commands/components later.
        rootCommands.set(3);
        typed.set(8);

        MagicUtilsConsumerInfo after = single();
        assertEquals(3, after.rootCommandCount());
        assertEquals(8, after.typedComponentCount());
    }

    @Test
    void findRebuildsFromLiveView() {
        AtomicInteger rootCommands = new AtomicInteger(1);
        MagicUtilsConsumerRegistry.register(meta(), view(rootCommands, new AtomicInteger(0), false));

        MagicUtilsConsumerInfo found = MagicUtilsConsumerRegistry.find("testmod");
        assertNotNull(found);
        assertEquals(1, found.rootCommandCount());

        rootCommands.set(5);
        assertEquals(5, MagicUtilsConsumerRegistry.find(MOD).rootCommandCount());
    }

    @Test
    void unregisterRemovesConsumer() {
        MagicUtilsConsumerRegistry.register(meta(), view(new AtomicInteger(0), new AtomicInteger(0), false));
        assertNotNull(MagicUtilsConsumerRegistry.find(MOD));
        MagicUtilsConsumerRegistry.unregister(MOD);
        assertNull(MagicUtilsConsumerRegistry.find(MOD));
    }

    @Test
    void closedFlagIsReadLive() {
        AtomicBoolean closed = new AtomicBoolean(false);
        MagicUtilsConsumerRegistry.register(meta(),
                new SimpleView(new AtomicInteger(2), new AtomicInteger(0), closed));
        assertTrue(!single().closed());
        closed.set(true);
        assertTrue(single().closed());
    }

    private static MagicUtilsConsumerInfo single() {
        List<MagicUtilsConsumerInfo> all = MagicUtilsConsumerRegistry.snapshot().stream()
                .filter(info -> info.pluginName().equals(MOD))
                .toList();
        assertEquals(1, all.size());
        return all.get(0);
    }

    private static MagicUtilsConsumerRuntimeView view(AtomicInteger rootCommands, AtomicInteger typed, boolean closed) {
        return new SimpleView(rootCommands, typed, new AtomicBoolean(closed));
    }

    private static final class SimpleView implements MagicUtilsConsumerRuntimeView {
        private final AtomicInteger rootCommands;
        private final AtomicInteger typed;
        private final AtomicBoolean closedFlag;

        private SimpleView(AtomicInteger rootCommands, AtomicInteger typed, AtomicBoolean closedFlag) {
            this.rootCommands = rootCommands;
            this.typed = typed;
            this.closedFlag = closedFlag;
        }

        @Override
        public int rootCommandCount() {
            return rootCommands.get();
        }

        @Override
        public int typedComponentCount() {
            return typed.get();
        }

        @Override
        public int namedComponentCount() {
            return 0;
        }

        @Override
        public List<String> namedComponentNames() {
            return List.of();
        }

        @Override
        public boolean diagnosticsEnabled() {
            return false;
        }

        @Override
        public boolean closed() {
            return closedFlag.get();
        }
    }
}
