package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks Bukkit/Paper plugins that are currently using the shared MagicUtils runtime.
 */
public final class BukkitMagicUtilsConsumerRegistry {
    private static final String BUNDLE_PLUGIN_NAME = "MagicUtils";
    private static final String REGISTER_METHOD = "registerSharedRuntimeConsumer";
    private static final String UNREGISTER_METHOD = "unregisterSharedRuntimeConsumer";
    private static final String KEY_PLUGIN_NAME = "pluginName";
    private static final String KEY_VERSION = "version";
    private static final String KEY_MAIN_CLASS = "mainClass";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_WEBSITE = "website";
    private static final String KEY_AUTHORS = "authors";
    private static final String KEY_PLATFORM_TYPE = "platformType";
    private static final String KEY_COMMANDS_ENABLED = "commandsEnabled";
    private static final String KEY_PERMISSION_PREFIX = "permissionPrefix";
    private static final String KEY_ROOT_COMMAND_COUNT = "rootCommandCount";
    private static final String KEY_DIAGNOSTICS_ENABLED = "diagnosticsEnabled";
    private static final String KEY_CLOSED = "closed";
    private static final String KEY_TYPED_COMPONENT_COUNT = "typedComponentCount";
    private static final String KEY_NAMED_COMPONENT_COUNT = "namedComponentCount";
    private static final String KEY_NAMED_COMPONENT_NAMES = "namedComponentNames";
    private static final String KEY_CONNECTED_AT_EPOCH_MILLIS = "connectedAtEpochMillis";

    private BukkitMagicUtilsConsumerRegistry() {
    }

    /**
     * Registers or refreshes a plugin using the shared MagicUtils runtime.
     *
     * @param plugin plugin instance
     * @param runtime runtime container
     * @param commandRegistry command registry when commands are enabled
     */
    public static void register(JavaPlugin plugin, MagicRuntime runtime, @Nullable CommandRegistry commandRegistry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runtime, "runtime");
        if (isBundlePlugin(plugin)) {
            return;
        }

        JavaPlugin bundlePlugin = resolveBundlePlugin(plugin);
        if (bundlePlugin == null || bundlePlugin == plugin) {
            return;
        }

        try {
            invokeBundleMethod(
                    bundlePlugin,
                    REGISTER_METHOD,
                    new Class<?>[]{JavaPlugin.class, Map.class},
                    plugin,
                    snapshotPayload(plugin, runtime, commandRegistry, Instant.now())
            );
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Failed to register shared MagicUtils consumer: " + exception.getMessage());
        }
    }

    /**
     * Removes a plugin from the shared runtime registry.
     *
     * @param plugin plugin instance
     */
    public static void unregister(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        if (isBundlePlugin(plugin)) {
            return;
        }

        JavaPlugin bundlePlugin = resolveBundlePlugin(plugin);
        if (bundlePlugin == null || bundlePlugin == plugin) {
            return;
        }

        try {
            invokeBundleMethod(bundlePlugin, UNREGISTER_METHOD, new Class<?>[]{JavaPlugin.class}, plugin);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Failed to unregister shared MagicUtils consumer: " + exception.getMessage());
        }
    }

    /**
     * Rebuilds an immutable consumer snapshot from a bridge payload.
     *
     * @param plugin plugin instance
     * @param payload registry payload emitted by the consumer runtime
     * @return immutable consumer info
     */
    public static ConsumerInfo consumerInfo(JavaPlugin plugin, Map<String, Object> payload) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(payload, "payload");
        var pluginMeta = plugin.getPluginMeta();
        return new ConsumerInfo(
                stringValue(payload, KEY_PLUGIN_NAME, plugin.getName()),
                stringValue(payload, KEY_VERSION, pluginMeta.getVersion()),
                stringValue(payload, KEY_MAIN_CLASS, pluginMeta.getMainClass()),
                nullableStringValue(payload, KEY_DESCRIPTION, pluginMeta.getDescription()),
                nullableStringValue(payload, KEY_WEBSITE, pluginMeta.getWebsite()),
                stringListValue(payload, KEY_AUTHORS, pluginMeta.getAuthors()),
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

    private static String normalizeKey(String pluginName) {
        return pluginName.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBundlePlugin(JavaPlugin plugin) {
        return BUNDLE_PLUGIN_NAME.equalsIgnoreCase(plugin.getName());
    }

    private static @Nullable JavaPlugin resolveBundlePlugin(JavaPlugin plugin) {
        Plugin bundlePlugin = plugin.getServer().getPluginManager().getPlugin(BUNDLE_PLUGIN_NAME);
        return bundlePlugin instanceof JavaPlugin javaPlugin ? javaPlugin : null;
    }

    private static void invokeBundleMethod(
            JavaPlugin bundlePlugin,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) throws ReflectiveOperationException {
        Method method = bundlePlugin.getClass().getMethod(methodName, parameterTypes);
        method.invoke(bundlePlugin, args);
    }

    private static Map<String, Object> snapshotPayload(
            JavaPlugin plugin,
            MagicRuntime runtime,
            @Nullable CommandRegistry commandRegistry,
            Instant connectedAt
    ) {
        var pluginMeta = plugin.getPluginMeta();
        Map<Class<?>, Object> typedComponents = runtime.components();
        Map<String, Object> namedComponents = runtime.namedComponents();
        List<String> namedComponentNames = new ArrayList<>(namedComponents.keySet());
        Collections.sort(namedComponentNames, String.CASE_INSENSITIVE_ORDER);
        boolean commandsEnabled = commandRegistry != null;
        int rootCommandCount = commandsEnabled ? commandRegistry.commandManager().getAll().size() : 0;
        String permissionPrefix = commandsEnabled ? commandRegistry.permissionPrefix() : null;
        boolean diagnosticsEnabled = runtime.findComponent(DiagnosticsService.class).isPresent();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_PLUGIN_NAME, plugin.getName());
        payload.put(KEY_VERSION, pluginMeta.getVersion());
        payload.put(KEY_MAIN_CLASS, pluginMeta.getMainClass());
        payload.put(KEY_DESCRIPTION, pluginMeta.getDescription());
        payload.put(KEY_WEBSITE, pluginMeta.getWebsite());
        payload.put(KEY_AUTHORS, List.copyOf(pluginMeta.getAuthors()));
        payload.put(KEY_PLATFORM_TYPE, platformTypeLabel(runtime));
        payload.put(KEY_COMMANDS_ENABLED, commandsEnabled);
        payload.put(KEY_PERMISSION_PREFIX, permissionPrefix);
        payload.put(KEY_ROOT_COMMAND_COUNT, rootCommandCount);
        payload.put(KEY_DIAGNOSTICS_ENABLED, diagnosticsEnabled);
        payload.put(KEY_CLOSED, runtime.isClosed());
        payload.put(KEY_TYPED_COMPONENT_COUNT, typedComponents.size());
        payload.put(KEY_NAMED_COMPONENT_COUNT, namedComponents.size());
        payload.put(KEY_NAMED_COMPONENT_NAMES, List.copyOf(namedComponentNames));
        payload.put(KEY_CONNECTED_AT_EPOCH_MILLIS, connectedAt.toEpochMilli());
        return payload;
    }

    private static String platformTypeLabel(MagicRuntime runtime) {
        String simpleName = runtime.platform().getClass().getSimpleName();
        if (simpleName.endsWith("PlatformProvider")) {
            simpleName = simpleName.substring(0, simpleName.length() - "PlatformProvider".length());
        }
        if (simpleName.endsWith("Platform")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Platform".length());
        }
        return simpleName;
    }

    private static String stringValue(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return fallback;
    }

    private static @Nullable String nullableStringValue(
            Map<String, Object> payload,
            String key,
            @Nullable String fallback
    ) {
        Object value = payload.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return fallback;
    }

    private static int intValue(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return fallback;
    }

    private static boolean booleanValue(Map<String, Object> payload, String key, boolean fallback) {
        Object value = payload.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return fallback;
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

    private static List<String> stringListValue(
            Map<String, Object> payload,
            String key,
            List<String> fallback
    ) {
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

    /**
     * Immutable snapshot of a shared-runtime consumer.
     *
     * @param pluginName plugin display name
     * @param version plugin version
     * @param mainClass plugin main class
     * @param description plugin description
     * @param website plugin website
     * @param authors plugin authors
     * @param platformType resolved Bukkit platform adapter type
     * @param commandsEnabled whether MagicUtils commands are enabled
     * @param permissionPrefix command permission prefix when commands are enabled
     * @param rootCommandCount number of registered root commands
     * @param diagnosticsEnabled whether diagnostics service is present
     * @param closed whether the runtime is closed
     * @param typedComponentCount typed component count
     * @param namedComponentCount named component count
     * @param namedComponentNames named component keys
     * @param connectedAt timestamp of the last registration
     */
    public record ConsumerInfo(
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
        /**
         * Returns true when this consumer matches the provided plugin name ignoring case.
         *
         * @param pluginName plugin name to compare
         * @return true when names match
         */
        public boolean matchesPlugin(String pluginName) {
            return pluginName != null && this.pluginName.equalsIgnoreCase(pluginName.trim());
        }

        /**
         * Returns a compact capabilities string for list output.
         *
         * @return human-readable capabilities summary
         */
        public String capabilitiesSummary() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("platform", platformType);
            fields.put("commands", commandsEnabled ? rootCommandCount + " roots" : "off");
            fields.put("diagnostics", diagnosticsEnabled ? "on" : "off");
            fields.put("components", typedComponentCount + "/" + namedComponentCount);
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
            return String.join(", ", parts);
        }
    }
}
