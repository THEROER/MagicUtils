package dev.ua.theroer.magicutils.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCodesAndFilesTest {

    @TempDir
    Path configDir;

    @Test
    void candidatesGoFromMostToLeastSpecific() {
        assertEquals(List.of("en_US", "en_us", "en-us", "en"), List.copyOf(LanguageCodes.candidates(" en_US ")));
        assertEquals(List.of("uk-UA", "uk-ua", "uk"), List.copyOf(LanguageCodes.candidates("uk-UA")));
        assertTrue(LanguageCodes.candidates("  ").isEmpty());
        assertTrue(LanguageCodes.candidates(null).isEmpty());
    }

    @Test
    void normalizeKeyIgnoresCaseAndSeparator() {
        assertEquals("pt-br", LanguageCodes.normalizeKey(" PT_br "));
        assertEquals("", LanguageCodes.normalizeKey(null));
    }

    @Test
    void filesAreDiscoveredByKnownExtensionsOnly() throws Exception {
        assertTrue(LanguageFiles.codesOnDisk(configDir).isEmpty(), "missing lang dir is not an error");

        Path lang = Files.createDirectories(configDir.resolve("lang"));
        Files.writeString(lang.resolve("en.jsonc"), "{}");
        Files.writeString(lang.resolve("uk.YML"), "a: b");
        Files.writeString(lang.resolve("notes.txt"), "x");
        Files.writeString(lang.resolve(".hidden"), "x");

        assertEquals(Set.of("en", "uk"), LanguageFiles.codesOnDisk(configDir));
        assertTrue(LanguageFiles.exists(configDir, "en"));
        assertFalse(LanguageFiles.exists(configDir, "notes"));
        assertFalse(LanguageFiles.exists(configDir, " "));
    }
}
