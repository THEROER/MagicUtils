package dev.ua.theroer.magicutils.lang;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationRegistryTest {

    @Test
    void registerTrimsAndMergesAndRejectsEmptyInput() {
        TranslationRegistry registry = new TranslationRegistry();

        Map<String, String> first = new HashMap<>();
        first.put(" plugin.a ", "A");
        first.put("  ", "blank key");
        first.put("plugin.null", null);
        assertEquals("uk", registry.register(" uk ", first));
        assertEquals("uk", registry.register("uk", Map.of("plugin.b", "B")));

        assertEquals("A", registry.lookup("uk", "plugin.a"));
        assertEquals("B", registry.lookup("uk", "plugin.b"));
        assertNull(registry.lookup("uk", "plugin.null"));

        assertNull(registry.register("uk", Map.of()));
        assertNull(registry.register(" ", Map.of("k", "v")));
        assertNull(registry.register("uk", Map.of(" ", "v")));
    }

    @Test
    void applyToKeepsExistingCustomMessages() {
        TranslationRegistry registry = new TranslationRegistry();
        registry.register("en", Map.of("plugin.kept", "registered", "plugin.added", "registered"));
        LanguageConfig config = new LanguageConfig();
        config.getCustomMessages().put("plugin.kept", "user");

        assertTrue(registry.applyTo(config, "en"));
        assertEquals("user", config.getCustomMessages().get("plugin.kept"));
        assertEquals("registered", config.getCustomMessages().get("plugin.added"));
        assertFalse(registry.applyTo(config, "en"), "a second pass has nothing left to add");
        assertFalse(registry.applyTo(config, "fr"));
    }

    @Test
    void lookupPrefersRegisteredOverBundledText() {
        TranslationRegistry registry = new TranslationRegistry();
        String key = "magicutils.commands.help.title";
        assertEquals("Help", registry.lookup("en", key));

        registry.register("en", Map.of(key, "Overridden"));

        assertEquals("Overridden", registry.lookup("en", key));
        assertNull(registry.lookup("en", "no.such.key"));
        assertNull(registry.lookup(null, key));
    }

    @Test
    void hasBundledReflectsShippedLanguageFiles() {
        TranslationRegistry registry = new TranslationRegistry();
        assertTrue(registry.hasBundled("en"));
        assertFalse(registry.hasBundled("zz_nowhere"));
    }

    @Test
    void initializeDefaultsStampsTheCodeOfAFreshFile() {
        TranslationRegistry registry = new TranslationRegistry();
        LanguageConfig config = new LanguageConfig();

        assertTrue(registry.initializeDefaults(config, "zz_nowhere", false));
        assertEquals("zz_nowhere", config.getMetadata().getCode());
        assertFalse(registry.initializeDefaults(config, "zz_nowhere", true),
                "an existing file with nothing bundled is left untouched");
    }
}
