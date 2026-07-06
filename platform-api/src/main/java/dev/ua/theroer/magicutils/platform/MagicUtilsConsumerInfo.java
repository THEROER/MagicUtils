package dev.ua.theroer.magicutils.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable, platform-independent snapshot of a plugin/mod that is using the
 * shared MagicUtils runtime. Both the Bukkit and Fabric bundle registries build
 * and expose these, so the record and the payload-decoding helpers live here in
 * platform-api rather than being duplicated per platform.
 *
 * @param pluginName consumer display name
 * @param version consumer version
 * @param mainClass consumer main class / entrypoint
 * @param description consumer description
 * @param website consumer website / contact
 * @param authors consumer authors
 * @param platformType resolved MagicUtils platform adapter type (e.g. Bukkit, Fabric)
 * @param commandsEnabled whether MagicUtils commands are enabled
 * @param permissionPrefix command permission prefix when commands are enabled
 * @param rootCommandCount number of registered root commands
 * @param diagnosticsEnabled whether the diagnostics service is present
 * @param closed whether the runtime is closed
 * @param typedComponentCount typed component count
 * @param namedComponentCount named component count
 * @param namedComponentNames named component keys
 * @param connectedAt timestamp of the last registration
 */
public record MagicUtilsConsumerInfo(
        String pluginName,
        String version,
        String mainClass,
        @Nullable String description,
        @Nullable String website,
        List<String> authors,
        String platformType,
        boolean commandsEnabled,
        @Nullable String permissionPrefix,
        int rootCommandCount,
        boolean diagnosticsEnabled,
        boolean closed,
        int typedComponentCount,
        int namedComponentCount,
        List<String> namedComponentNames,
        Instant connectedAt
) {
    // Payload keys shared by the registry bridges on every platform.
    public static final String KEY_PLUGIN_NAME = "pluginName";
    public static final String KEY_VERSION = "version";
    public static final String KEY_MAIN_CLASS = "mainClass";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_WEBSITE = "website";
    public static final String KEY_AUTHORS = "authors";
    public static final String KEY_PLATFORM_TYPE = "platformType";
    public static final String KEY_COMMANDS_ENABLED = "commandsEnabled";
    public static final String KEY_PERMISSION_PREFIX = "permissionPrefix";
    public static final String KEY_ROOT_COMMAND_COUNT = "rootCommandCount";
    public static final String KEY_DIAGNOSTICS_ENABLED = "diagnosticsEnabled";
    public static final String KEY_CLOSED = "closed";
    public static final String KEY_TYPED_COMPONENT_COUNT = "typedComponentCount";
    public static final String KEY_NAMED_COMPONENT_COUNT = "namedComponentCount";
    public static final String KEY_NAMED_COMPONENT_NAMES = "namedComponentNames";
    public static final String KEY_CONNECTED_AT_EPOCH_MILLIS = "connectedAtEpochMillis";

    /** Returns true when this consumer matches [pluginName] ignoring case. */
    public boolean matchesPlugin(@Nullable String pluginName) {
        return pluginName != null && this.pluginName.equalsIgnoreCase(pluginName.trim());
    }

    /** Compact capabilities string for list output. */
    public String capabilitiesSummary() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("platform", platformType);
        fields.put("commands", commandsEnabled ? rootCommandCount + " roots" : "off");
        fields.put("diagnostics", diagnosticsEnabled ? "on" : "off");
        fields.put("components", typedComponentCount + "t/" + namedComponentCount + "n");
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", parts);
    }

    /**
     * Builds a point-in-time snapshot from static metadata plus a live {@code
     * view}. The static fields never change after registration; the dynamic
     * fields (command/component counts, diagnostics, closed) are read from the
     * view at call time, so each snapshot reflects the consumer's current state.
     * A {@code null} view yields a consumer with zeroed dynamic fields.
     */
    public static MagicUtilsConsumerInfo fromStatic(
            String pluginName,
            String version,
            String mainClass,
            @Nullable String description,
            @Nullable String website,
            List<String> authors,
            String platformType,
            boolean commandsEnabled,
            @Nullable String permissionPrefix,
            Instant connectedAt,
            @Nullable MagicUtilsConsumerRuntimeView view
    ) {
        List<String> namedNames = view != null ? new ArrayList<>(view.namedComponentNames()) : new ArrayList<>();
        namedNames.sort(String.CASE_INSENSITIVE_ORDER);
        return new MagicUtilsConsumerInfo(
                pluginName,
                version,
                mainClass,
                description,
                website,
                List.copyOf(authors),
                platformType,
                commandsEnabled,
                permissionPrefix,
                commandsEnabled && view != null ? view.rootCommandCount() : 0,
                view != null && view.diagnosticsEnabled(),
                view != null && view.closed(),
                view != null ? view.typedComponentCount() : 0,
                view != null ? view.namedComponentCount() : 0,
                List.copyOf(namedNames),
                connectedAt
        );
    }

    /**
     * Rebuilds an immutable consumer snapshot from a bridge [payload]. The
     * fallbacks apply when a key is absent or of the wrong type, so a partial
     * payload still yields a usable record.
     */
    public static MagicUtilsConsumerInfo fromPayload(
            Map<String, Object> payload,
            String fallbackName,
            String fallbackVersion,
            String fallbackMainClass,
            @Nullable String fallbackDescription,
            @Nullable String fallbackWebsite,
            List<String> fallbackAuthors
    ) {
        return new MagicUtilsConsumerInfo(
                stringValue(payload, KEY_PLUGIN_NAME, fallbackName),
                stringValue(payload, KEY_VERSION, fallbackVersion),
                stringValue(payload, KEY_MAIN_CLASS, fallbackMainClass),
                nullableStringValue(payload, KEY_DESCRIPTION, fallbackDescription),
                nullableStringValue(payload, KEY_WEBSITE, fallbackWebsite),
                stringListValue(payload, KEY_AUTHORS, fallbackAuthors),
                stringValue(payload, KEY_PLATFORM_TYPE, "Unknown"),
                booleanValue(payload, KEY_COMMANDS_ENABLED, false),
                nullableStringValue(payload, KEY_PERMISSION_PREFIX, null),
                intValue(payload, KEY_ROOT_COMMAND_COUNT, 0),
                booleanValue(payload, KEY_DIAGNOSTICS_ENABLED, false),
                booleanValue(payload, KEY_CLOSED, false),
                intValue(payload, KEY_TYPED_COMPONENT_COUNT, 0),
                intValue(payload, KEY_NAMED_COMPONENT_COUNT, 0),
                stringListValue(payload, KEY_NAMED_COMPONENT_NAMES, List.of()),
                instantValue(payload, KEY_CONNECTED_AT_EPOCH_MILLIS, Instant.now())
        );
    }

    private static String stringValue(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : fallback;
    }

    private static @Nullable String nullableStringValue(Map<String, Object> payload, String key, @Nullable String fallback) {
        Object value = payload.get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : fallback;
    }

    private static int intValue(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        return value instanceof Number numberValue ? numberValue.intValue() : fallback;
    }

    private static boolean booleanValue(Map<String, Object> payload, String key, boolean fallback) {
        Object value = payload.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private static Instant instantValue(Map<String, Object> payload, String key, Instant fallback) {
        Object value = payload.get(key);
        if (value instanceof Number numberValue) {
            return Instant.ofEpochMilli(numberValue.longValue());
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Instant.parse(stringValue);
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<String> stringListValue(Map<String, Object> payload, String key, List<String> fallback) {
        Object value = payload.get(key);
        if (!(value instanceof Iterable<?> iterable)) {
            return List.copyOf(fallback);
        }
        List<String> strings = new ArrayList<>();
        for (Object entry : iterable) {
            if (entry instanceof String stringValue && !stringValue.isBlank()) {
                strings.add(stringValue);
            }
        }
        return List.copyOf(strings);
    }
}
