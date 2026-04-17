package dev.ua.theroer.magicutils.platform.fabric.codec;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for detecting and creating appropriate {@link TextSerializationAdapter} instances.
 */
public final class TextSerializationAdapters {
    private TextSerializationAdapters() {
    }

    /**
     * Detects the best available text serialization adapter for the current environment.
     * Tries modern {@link YarnTextCodecsAdapter} first, then falls back to {@link LegacyTextSerializerAdapter}.
     *
     * @param logger the logger to report detection progress and errors (can be null)
     * @return a working {@link TextSerializationAdapter}
     * @throws IllegalStateException if no supported serializer is found
     */
    public static TextSerializationAdapter detect(Logger logger) {
        List<String> attempts = new ArrayList<>();
        try {
            TextSerializationAdapter adapter = new YarnTextCodecsAdapter();
            if (logger != null) {
                logger.info("Using Fabric text serialization adapter: {}", adapter.name());
            }
            return adapter;
        } catch (RuntimeException error) {
            attempts.add("TextCodecs adapter failed: " + error.getMessage());
            if (logger != null) {
                logger.debug("TextCodecs adapter unavailable: {}", describe(error));
            }
        }

        try {
            TextSerializationAdapter adapter = new LegacyTextSerializerAdapter();
            if (logger != null) {
                logger.info("Using Fabric text serialization adapter: {}", adapter.name());
            }
            return adapter;
        } catch (RuntimeException error) {
            attempts.add("Legacy serializer adapter failed: " + error.getMessage());
            if (logger != null) {
                logger.debug("Legacy serializer adapter unavailable: {}", describe(error));
            }
        }

        String msg = "No supported Fabric text serializer found. "
                + "MagicUtils plain mode is disabled. "
                + "Checked codec-based and legacy serializer paths.";
        if (logger != null) {
            logger.error("{} Attempts: {}", msg, attempts);
        }
        throw new IllegalStateException(msg + " Attempts: " + attempts);
    }

    private static String describe(RuntimeException error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message != null && !message.isBlank()
                ? message
                : error.getClass().getName();
    }
}
