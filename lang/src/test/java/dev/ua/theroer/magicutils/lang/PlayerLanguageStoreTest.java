package dev.ua.theroer.magicutils.lang;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLanguageStoreTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void explicitChoiceWinsOverDetectedLocale() {
        PlayerLanguageStore store = new PlayerLanguageStore();
        assertEquals("en", store.resolve(player, "en"));

        store.put(true, player, "uk");
        assertEquals("uk", store.resolve(player, "en"));

        store.put(false, player, "de");
        assertEquals("de", store.resolve(player, "en"));

        store.remove(false, player);
        assertEquals("uk", store.resolve(player, "en"));
        store.remove(true, player);
        assertEquals("en", store.resolve(player, "en"));
    }

    @Test
    void nullPlayerFallsBackAndRemoveToleratesIt() {
        PlayerLanguageStore store = new PlayerLanguageStore();
        store.remove(false, null);
        assertEquals("en", store.resolve(null, "en"));
    }

    @Test
    void snapshotsAreSeparateAndImmutable() {
        PlayerLanguageStore store = new PlayerLanguageStore();
        store.put(false, player, "de");
        store.put(true, player, "uk");

        Map<UUID, String> explicit = store.snapshot(false);
        assertEquals(Map.of(player, "de"), explicit);
        assertEquals(Map.of(player, "uk"), store.snapshot(true));
        assertThrows(UnsupportedOperationException.class, () -> explicit.put(UUID.randomUUID(), "x"));

        store.remove(false, player);
        assertTrue(explicit.containsKey(player), "a snapshot does not track later changes");
    }
}
