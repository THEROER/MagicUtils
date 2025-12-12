package dev.ua.theroer.magicutils.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Utility methods for color calculations and MiniMessage helper conversions.
 */
public final class ColorUtils {

    private static final Map<Character, String> LEGACY_TO_MM = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private ColorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Adjust color brightness by factor.
     * 
     * @param hex    the hex color string (e.g., "#ff0000")
     * @param factor brightness multiplier (1.0 = no change, >1.0 = brighter)
     * @return adjusted hex color string
     */
    public static String adjustBrightness(String hex, float factor) {
        int r = Integer.valueOf(hex.substring(1, 3), 16);
        int g = Integer.valueOf(hex.substring(3, 5), 16);
        int b = Integer.valueOf(hex.substring(5, 7), 16);

        r = Math.min(255, Math.round(r * factor));
        g = Math.min(255, Math.round(g * factor));
        b = Math.min(255, Math.round(b * factor));

        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Adjust color hue by degrees.
     * 
     * @param hex      the hex color string (e.g., "#ff0000")
     * @param hueShift degrees to shift hue (-360 to 360)
     * @return adjusted hex color string
     */
    public static String adjustHue(String hex, int hueShift) {
        int r = Integer.valueOf(hex.substring(1, 3), 16);
        int g = Integer.valueOf(hex.substring(3, 5), 16);
        int b = Integer.valueOf(hex.substring(5, 7), 16);

        int shift = Math.abs(hueShift) % 360;
        if (shift > 120) {
            int temp = r;
            r = g;
            g = b;
            b = temp;
        } else if (shift > 60) {
            int temp = r;
            r = b;
            b = g;
            g = temp;
        }

        return String.format("#%02x%02x%02x", r, g, b);
    }

    /**
     * Get primary and secondary colors derived from plugin name.
     * 
     * @param name the plugin name to generate colors from
     * @return array containing [primary, secondary] hex colors
     */
    public static String[] getMainAndSecondaryColor(String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(name.getBytes(StandardCharsets.UTF_8));

            int r = 100 + (hash[0] & 0xFF) % 156;
            int g = 100 + (hash[1] & 0xFF) % 156;
            int b = 100 + (hash[2] & 0xFF) % 156;

            String primary = String.format("#%02x%02x%02x", r, g, b);

            int r2 = Math.min(255, r + 40);
            int g2 = Math.min(255, g + 50);
            int b2 = Math.max(50, b - 20);

            String secondary = String.format("#%02x%02x%02x", r2, g2, b2);

            return new String[] { primary, secondary };
        } catch (NoSuchAlgorithmException e) {
            return new String[] { "#7c3aed", "#ec4899" };
        }
    }

    /**
     * Create gradient tag from colors array.
     * 
     * @param colors array of hex color strings
     * @return MiniMessage gradient tag (e.g., "&lt;gradient:#ff0000:#00ff00&gt;")
     */
    public static String createGradientTag(String[] colors) {
        StringBuilder sb = new StringBuilder("<gradient");
        for (String color : colors) {
            sb.append(":").append(color);
        }
        sb.append(">");
        return sb.toString();
    }

    /**
     * Converts legacy color codes to MiniMessage codes.
     * 
     * @param input string with legacy color codes (&amp;a, &amp;#ff0000, etc.)
     * @return string with MiniMessage tags (&lt;green&gt;, &lt;#ff0000&gt;, etc.)
     */
    public static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String s = input.replace('§', '&');

        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length();) {
            if (i + 13 < s.length()
                    && s.charAt(i) == '&'
                    && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')
                    && s.charAt(i + 2) == '&' && s.charAt(i + 4) == '&'
                    && s.charAt(i + 6) == '&' && s.charAt(i + 8) == '&'
                    && s.charAt(i + 10) == '&' && s.charAt(i + 12) == '&') {

                char a = s.charAt(i + 3), b = s.charAt(i + 5), c = s.charAt(i + 7),
                        d = s.charAt(i + 9), e = s.charAt(i + 11), f = s.charAt(i + 13);
                String hex = ("" + a + b + c + d + e + f).toLowerCase();
                out.append("<#").append(hex).append(">");
                i += 14;
                continue;
            }
            if (i + 7 <= s.length() && s.charAt(i) == '&' && s.charAt(i + 1) == '#') {
                String hex = s.substring(i + 2, i + 8);
                if (hex.matches("(?i)[0-9a-f]{6}")) {
                    out.append("<#").append(hex).append(">");
                    i += 8;
                    continue;
                }
            }
            if (i + 1 < s.length() && s.charAt(i) == '&') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String tag = LEGACY_TO_MM.get(code);
                if (tag != null) {
                    out.append(tag);
                    i += 2;
                    continue;
                }
            }
            out.append(s.charAt(i++));
        }
        return out.toString();
    }
}

