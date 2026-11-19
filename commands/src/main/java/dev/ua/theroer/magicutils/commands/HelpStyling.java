package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.config.logger.HelpSettings;
import dev.ua.theroer.magicutils.config.logger.LoggerConfig;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.utils.ColorUtils;

/**
 * Help output styling: palette resolution from logger settings and MiniMessage tag helpers.
 */
final class HelpStyling {

    private HelpStyling() {
    }

    static final HelpStyle DEFAULT_STYLE = new HelpStyle(
            "#ff55ff",
            "gray",
            "white",
            "#ff55ff",
            "-----------------------------",
            7,
            8
    );

    record HelpStyle(String primaryTag,
                     String mutedTag,
                     String textTag,
                     String lineTag,
                     String lineText,
                     int pageSize,
                     int maxEnumValues) {
    }
    static HelpStyle resolveStyle(LoggerCore logger) {
        HelpStyle base = DEFAULT_STYLE;
        if (logger == null) {
            return base;
        }
        LoggerConfig config = logger.getConfig();
        if (config == null) {
            return base;
        }
        HelpSettings help = config.getHelp();
        if (help == null) {
            return base;
        }

        int pageSize = help.getPageSize() > 0 ? help.getPageSize() : base.pageSize();
        int maxEnumValues = help.getMaxEnumValues() > 0 ? help.getMaxEnumValues() : base.maxEnumValues();
        String lineText = safeLine(help.getLine(), base.lineText());

        if (help.isUseLoggerColors()) {
            String[] palette = config.resolveColors(LogLevel.INFO, false);
            String primary = firstColor(palette, base.primaryTag());
            String muted = mutedFrom(primary, base.mutedTag());
            String text = normalizeTag(help.getTextColor(), base.textTag());
            String lineTag = palette != null && palette.length > 1
                    ? ColorUtils.createGradientTag(palette)
                    : primary;
            return new HelpStyle(primary, muted, text, lineTag, lineText, pageSize, maxEnumValues);
        }

        String primary = normalizeTag(help.getPrimaryColor(), base.primaryTag());
        String muted = normalizeTag(help.getMutedColor(), base.mutedTag());
        String text = normalizeTag(help.getTextColor(), base.textTag());
        return new HelpStyle(primary, muted, text, primary, lineText, pageSize, maxEnumValues);
    }

    static String normalizeTag(String tag, String fallback) {
        if (tag == null) {
            return fallback;
        }
        String trimmed = tag.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static String safeLine(String line, String fallback) {
        if (line == null) {
            return fallback;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static String firstColor(String[] colors, String fallback) {
        if (colors == null || colors.length == 0) {
            return fallback;
        }
        String primary = colors[0];
        return normalizeTag(primary, fallback);
    }

    static String mutedFrom(String primary, String fallback) {
        if (primary == null) {
            return fallback;
        }
        String trimmed = primary.trim();
        if (trimmed.startsWith("#") && trimmed.length() == 7) {
            return ColorUtils.adjustBrightness(trimmed, 0.7f);
        }
        return fallback;
    }

    static String color(String tag, String text) {
        if (text == null) {
            return "";
        }
        if (tag == null || tag.isEmpty()) {
            return text;
        }
        return openTag(tag) + text + closeTag(tag);
    }

    static String openTag(String tag) {
        String trimmed = tag.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed;
        }
        return "<" + trimmed + ">";
    }

    static String closeTag(String tag) {
        String trimmed = tag.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            String name = inner.split(":", 2)[0];
            return "</" + name + ">";
        }
        return "</" + trimmed + ">";
    }

    static String escapeMiniText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = text.replace("\\", "\\\\");
        escaped = escaped.replace("<", "\\<").replace(">", "\\>");
        return escaped;
    }
}
