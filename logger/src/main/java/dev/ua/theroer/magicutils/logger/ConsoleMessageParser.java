package dev.ua.theroer.magicutils.logger;

import java.util.Locale;

/**
 * Parses console log messages produced by MagicUtils to extract level and sub-logger prefix.
 */
public final class ConsoleMessageParser {
    private ConsoleMessageParser() {
    }

    /**
     * Parses a console message and extracts level, sub-logger, and prefix text.
     *
     * @param message raw console message
     * @return parsed message metadata
     */
    public static ParsedMessage parse(String message) {
        if (message == null || message.isEmpty()) {
            return new ParsedMessage(message, LogLevel.INFO, null, "");
        }

        int index = 0;
        int length = message.length();
        while (index < length && Character.isWhitespace(message.charAt(index))) {
            index++;
        }
        if (index >= length || message.charAt(index) != '[') {
            return new ParsedMessage(message, LogLevel.INFO, null, "");
        }

        Segment primary = readSegment(message, index);
        if (primary == null) {
            return new ParsedMessage(message, LogLevel.INFO, null, "");
        }
        index = skipWhitespace(message, primary.end);

        String secondary = null;
        if (index < length && message.charAt(index) == '[') {
            Segment second = readSegment(message, index);
            if (second != null) {
                secondary = second.value;
                index = skipWhitespace(message, second.end);
            }
        }

        LogLevel level = parseLevel(primary.value);
        int prefixEnd = Math.min(index, length);
        String prefixText = message.substring(0, prefixEnd);
        String remaining = message.substring(prefixEnd);
        return new ParsedMessage(remaining, level, secondary, prefixText);
    }

    private static Segment readSegment(String message, int start) {
        if (start < 0 || start >= message.length() || message.charAt(start) != '[') {
            return null;
        }
        int end = message.indexOf(']', start + 1);
        if (end == -1) {
            return null;
        }
        String value = message.substring(start + 1, end);
        return new Segment(value, end + 1);
    }

    private static int skipWhitespace(String message, int index) {
        int length = message.length();
        while (index < length && Character.isWhitespace(message.charAt(index))) {
            index++;
        }
        return index;
    }

    private static LogLevel parseLevel(String segment) {
        if (segment == null || segment.isBlank()) {
            return LogLevel.INFO;
        }
        String[] parts = segment.trim().split("\\s+");
        if (parts.length == 0) {
            return LogLevel.INFO;
        }
        String last = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        return switch (last) {
            case "DEBUG" -> LogLevel.DEBUG;
            case "WARN" -> LogLevel.WARN;
            case "ERROR" -> LogLevel.ERROR;
            case "SUCCESS" -> LogLevel.SUCCESS;
            case "TRACE" -> LogLevel.TRACE;
            default -> LogLevel.INFO;
        };
    }

    private static final class Segment {
        private final String value;
        private final int end;

        private Segment(String value, int end) {
            this.value = value;
            this.end = end;
        }
    }

    /**
     * Parsed representation of a console message.
     */
    public static final class ParsedMessage {
        private final String message;
        private final LogLevel level;
        private final String subLogger;
        private final String prefixText;

        private ParsedMessage(String message, LogLevel level, String subLogger, String prefixText) {
            this.message = message;
            this.level = level;
            this.subLogger = subLogger;
            this.prefixText = prefixText;
        }

        /**
         * Returns the message body without the parsed prefix.
         *
         * @return message body
         */
        public String message() {
            return message;
        }

        /**
         * Returns the parsed log level.
         *
         * @return log level
         */
        public LogLevel level() {
            return level;
        }

        /**
         * Returns the parsed sub-logger name, if any.
         *
         * @return sub-logger name or null
         */
        public String subLogger() {
            return subLogger;
        }

        /**
         * Returns the raw prefix text that was parsed.
         *
         * @return prefix text
         */
        public String prefixText() {
            return prefixText;
        }
    }
}
