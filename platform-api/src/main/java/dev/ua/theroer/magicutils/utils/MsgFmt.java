package dev.ua.theroer.magicutils.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single placeholder substitution engine for the whole library.
 *
 * <p>Resolves {@code {name}} tokens in a template using:</p>
 * <ul>
 *     <li>a single {@code Map<String, ?>} as the only argument,</li>
 *     <li>or a flat varargs list — interpreted positionally against
 *         the order in which placeholder names first appear in the
 *         template,</li>
 *     <li>or an {@code Iterable<?>} / array as the only argument,
 *         treated like the flat varargs list.</li>
 * </ul>
 *
 * <p>If the template contains no {@code {curly}} tokens at all and
 * arguments are supplied, falls back to {@link String#format} so legacy
 * {@code "%s"}-style format strings still work.</p>
 *
 * <p>An optional {@code valueTransformer} runs on every replacement
 * before substitution. The lang module passes a
 * {@code MiniMessage::escapeTags} transformer here to neutralize tags
 * coming from user-supplied placeholders. Keeping the transformer
 * pluggable means {@code platform-api} stays free of a hard MiniMessage
 * dependency.</p>
 */
public final class MsgFmt {
    private static final Pattern CURLY = Pattern.compile("\\{([a-zA-Z0-9_.-]+)\\}");

    private MsgFmt() {
    }

    /**
     * Substitutes placeholders without value transformation.
     *
     * @param template message template (may contain {@code {name}} tokens)
     * @param placeholders Map / Iterable / array / flat varargs of values
     * @return message with placeholders applied, or the original
     *         template when nothing matched
     */
    public static String apply(String template, Object... placeholders) {
        return apply(template, null, placeholders);
    }

    /**
     * Substitutes placeholders, transforming each replacement value
     * through {@code valueTransformer} before insertion.
     *
     * <p>Pass {@code null} (or {@link Function#identity()}) for no
     * transformation. Pass a tag-escape function (e.g.
     * {@code MiniMessage::escapeTags} after a {@code String::valueOf})
     * to make user-supplied data safe for downstream parsing.</p>
     *
     * @param template message template
     * @param valueTransformer optional transformer applied to each
     *                         replacement (after {@code String.valueOf})
     * @param placeholders Map / Iterable / array / flat varargs of values
     * @return message with placeholders applied
     */
    public static String apply(String template,
                               Function<String, String> valueTransformer,
                               Object... placeholders) {
        if (template == null) {
            return null;
        }
        if (placeholders == null || placeholders.length == 0) {
            return template;
        }

        if (!containsCurly(template)) {
            return String.format(Locale.ROOT, template, placeholders);
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
            return replaceWithMap(template, asStrings, valueTransformer);
        }

        List<?> values;
        if (placeholders.length == 1 && first instanceof Iterable<?>) {
            values = toList((Iterable<?>) first);
        } else if (placeholders.length == 1 && first != null && first.getClass().isArray()) {
            values = Arrays.asList((Object[]) first);
        } else if (looksLikeKeyValuePairs(placeholders)) {
            return replaceWithKeyValuePairs(template, placeholders, valueTransformer);
        } else {
            values = Arrays.asList(placeholders);
        }
        return replaceWithOrderedList(template, values, valueTransformer);
    }

    /**
     * Convenience overload: returns a builder-style argument list from
     * key/value pairs. Equivalent to {@code Map.of(k1,v1,k2,v2,...)}
     * but returns a {@link Map} object that {@link #apply} accepts.
     *
     * @param keyValuePairs even-length array of {@code key, value, ...}
     * @return ordered map suitable for {@code apply(template, args(...))}
     */
    public static Map<String, Object> args(Object... keyValuePairs) {
        if (keyValuePairs == null || keyValuePairs.length == 0) {
            return Map.of();
        }
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "MsgFmt.args: expected even number of key/value pairs, got " + keyValuePairs.length);
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(keyValuePairs.length / 2);
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    private static boolean containsCurly(String s) {
        return s.indexOf('{') >= 0 && s.indexOf('}') > s.indexOf('{');
    }

    private static boolean looksLikeKeyValuePairs(Object[] args) {
        if (args.length < 2 || args.length % 2 != 0) {
            return false;
        }
        for (int i = 0; i < args.length; i += 2) {
            if (!(args[i] instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static String replaceWithKeyValuePairs(String template,
                                                    Object[] pairs,
                                                    Function<String, String> transformer) {
        Map<String, Object> map = new HashMap<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return replaceWithMap(template, map, transformer);
    }

    private static String replaceWithMap(String template,
                                          Map<String, ?> map,
                                          Function<String, String> transformer) {
        Matcher matcher = CURLY.matcher(template);
        StringBuffer sb = new StringBuffer(template.length() + 16);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!map.containsKey(name)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replacement = transform(String.valueOf(map.get(name)), transformer);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceWithOrderedList(String template,
                                                  List<?> values,
                                                  Function<String, String> transformer) {
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
            String replacement = transform(String.valueOf(binding.get(name)), transformer);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String transform(String value, Function<String, String> transformer) {
        if (transformer == null) {
            return value;
        }
        String transformed = transformer.apply(value);
        return transformed != null ? transformed : "";
    }

    private static List<?> toList(Iterable<?> iterable) {
        ArrayList<Object> list = new ArrayList<>();
        for (Object o : iterable) {
            list.add(o);
        }
        return list;
    }
}
