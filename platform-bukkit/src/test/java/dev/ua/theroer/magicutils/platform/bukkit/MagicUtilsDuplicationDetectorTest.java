package dev.ua.theroer.magicutils.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the JVM-global duplication detector: it stays quiet for a single
 * MagicUtils host and warns once, naming the hosts, when a second appears.
 */
class MagicUtilsDuplicationDetectorTest {

    private static final String HOSTS_PROPERTY = "magicutils.bukkit.hosts";
    private static final String WARNED_PROPERTY = "magicutils.bukkit.duplicationWarned";

    @BeforeEach
    @AfterEach
    void clearGlobalState() {
        System.clearProperty(HOSTS_PROPERTY);
        System.clearProperty(WARNED_PROPERTY);
    }

    @Test
    void singleHostDoesNotWarn() {
        CapturingLogger logger = new CapturingLogger();
        MagicUtilsDuplicationDetector.record("AliasCreator", "2.1", logger.logger());

        assertTrue(logger.warnings().isEmpty(), "a single MagicUtils host must not warn");
    }

    @Test
    void secondHostWarnsOnceAndNamesBothHosts() {
        CapturingLogger first = new CapturingLogger();
        CapturingLogger second = new CapturingLogger();
        CapturingLogger third = new CapturingLogger();

        MagicUtilsDuplicationDetector.record("AliasCreator", "2.1", first.logger());
        MagicUtilsDuplicationDetector.record("DonateMenu", "1.4", second.logger());
        MagicUtilsDuplicationDetector.record("MagicUtils", "1.22.1", third.logger());

        assertTrue(first.warnings().isEmpty(), "the first host cannot yet see a duplicate");
        assertEquals(1, second.warnings().size(), "the second host warns exactly once");
        assertTrue(third.warnings().isEmpty(), "later hosts stay quiet once warned");

        String warning = second.warnings().get(0);
        assertTrue(warning.contains("AliasCreator 2.1"), warning);
        assertTrue(warning.contains("DonateMenu 1.4"), warning);
        assertTrue(warning.contains("bundled in DonateMenu"), warning);
        assertTrue(warning.contains("-Pmagicutils_embed=false"), warning);
    }

    @Test
    void standaloneHostIsLabelledStandalone() {
        MagicUtilsDuplicationDetector.record("MagicUtils", "1.22.1", new CapturingLogger().logger());
        CapturingLogger consumer = new CapturingLogger();
        MagicUtilsDuplicationDetector.record("AliasCreator", "2.1", consumer.logger());

        String warning = consumer.warnings().get(0);
        assertTrue(warning.contains("standalone plugin MagicUtils"), warning);
    }

    /** A JUL logger with an attached handler that records WARNING messages. */
    private static final class CapturingLogger {
        private final Logger logger;
        private final List<String> warnings = new ArrayList<>();

        CapturingLogger() {
            this.logger = Logger.getAnonymousLogger();
            this.logger.setUseParentHandlers(false);
            this.logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record.getLevel() == Level.WARNING) {
                        warnings.add(record.getMessage());
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }

        Logger logger() {
            return logger;
        }

        List<String> warnings() {
            return warnings;
        }
    }
}
