package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Shared helpers for parsing MiniMessage/legacy hybrid strings.
 */
public final class MessageParser {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(false).build();

    private MessageParser() {
    }

    /**
     * Parse a string that may contain legacy colors and/or MiniMessage tags.
     *
     * @param input source string
     * @return parsed component (never null)
     */
    public static Component parseSmart(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        boolean hasMini = input.indexOf('<') >= 0 && input.indexOf('>') > input.indexOf('<');
        boolean hasLegacy = input.indexOf('&') >= 0 || input.indexOf('§') >= 0;
        String source = input;
        if (hasLegacy) {
            source = ColorUtils.legacyToMiniMessage(source);
            hasMini = true;
        }
        Component comp = hasMini ? MINI_MESSAGE.deserialize(source) : Component.text(source);
        if (comp.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            comp = comp.decoration(TextDecoration.ITALIC, false);
        }
        return comp;
    }

    /**
     * Escapes a value for use inside a MiniMessage tag attribute (e.g. the target
     * of {@code <click:run_command:"...">} or {@code <hover:show_text:"...">}), so
     * quotes and tag brackets in the value don't break the surrounding tag.
     *
     * @param value raw attribute value (null yields empty string)
     * @return escaped value safe to embed in a quoted MiniMessage attribute
     */
    public static String escapeAttribute(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }
}
