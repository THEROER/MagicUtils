package dev.ua.theroer.magicutils.platform.fabric.codec;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class TextSerializationAdapters {
    private TextSerializationAdapters() {
    }

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
                logger.warn("TextCodecs adapter unavailable: {}", error.toString());
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
                logger.warn("Legacy serializer adapter unavailable: {}", error.toString());
            }
        }

        String msg = "No supported Fabric text serializer found. "
                + "MagicUtils plain mode is disabled. "
                + "Checked TextCodecs and legacy Text$Serializer paths.";
        if (logger != null) {
            logger.error("{} Attempts: {}", msg, attempts);
        }
        throw new IllegalStateException(msg + " Attempts: " + attempts);
    }
}
