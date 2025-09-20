package dev.ua.theroer.magicutils.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for applying positional or named placeholders in message templates.
 */
public final class MsgFmt {
    private static final Pattern CURLY = Pattern.compile("\\{([a-zA-Z0-9_.-]+)\\}");

    private MsgFmt() {
    }

    /**
     * Applies placeholders to a message string.
     * 
     * @param messageStr   the message string to apply placeholders to
     * @param placeholders the placeholders to apply to the message
     * @return the message string with placeholders applied
     */
    public static String apply(String messageStr, Object... placeholders) {
        if (messageStr == null) {
            return null;
        }
        if (placeholders == null || placeholders.length == 0) {
            return messageStr;
        }

        if (!containsCurly(messageStr)) {
            return String.format(Locale.ROOT, messageStr, placeholders);
        }

        Object first = placeholders[0];

        if (first instanceof Map) {
            if (placeholders.length > 1) {
                throw new IllegalArgumentException(
                        "MsgFmt.apply: when first arg is Map, only a single argument is allowed.");
            }

            Map<?, ?> raw = (Map<?, ?>) first;
            Map<String, Object> asStrings = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                asStrings.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return replaceWithMap(messageStr, asStrings);
        }

        List<?> values;
        if (placeholders.length == 1 && first instanceof Iterable<?>) {
            values = toList((Iterable<?>) first);
        } else if (placeholders.length == 1 && first != null && first.getClass().isArray()) {
            values = Arrays.asList((Object[]) first);
        } else {
            values = Arrays.asList(placeholders);
        }
        return replaceWithOrderedList(messageStr, values);
    }

    private static boolean containsCurly(String s) {
        return s.indexOf('{') >= 0 && s.indexOf('}') > s.indexOf('{');
    }

    private static String replaceWithMap(String template, Map<String, ?> map) {
        Matcher matcher = CURLY.matcher(template);
        StringBuffer sb = new StringBuffer(template.length() + 16);
        while (matcher.find()) {
            String name = matcher.group(1);
            boolean hasKey = map.containsKey(name);
            Object value = hasKey ? map.get(name) : null;

            if (!hasKey) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String replacement = String.valueOf(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceWithOrderedList(String template, List<?> values) {
        LinkedHashMap<String, Integer> nameToIndex = new LinkedHashMap<>();
        Matcher scan = CURLY.matcher(template);
        while (scan.find()) {
            String name = scan.group(1);
            nameToIndex.putIfAbsent(name, nameToIndex.size());
        }

        int uniqueCount = nameToIndex.size();
        if (values.size() < uniqueCount) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "MsgFmt.apply: not enough values for named placeholders. Need %d, got %d.",
                    uniqueCount, values.size()));
        }

        Map<String, Object> binding = new HashMap<>(uniqueCount * 2);
        int i = 0;
        for (String name : nameToIndex.keySet()) {
            binding.put(name, values.get(i++));
        }

        Matcher matcher = CURLY.matcher(template);
        StringBuffer sb = new StringBuffer(template.length() + 16);
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = binding.get(name);
            String replacement = String.valueOf(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static List<?> toList(Iterable<?> iterable) {
        ArrayList<Object> list = new ArrayList<>();
        for (Object o : iterable) {
            list.add(o);
        }
        return list;
    }
}
