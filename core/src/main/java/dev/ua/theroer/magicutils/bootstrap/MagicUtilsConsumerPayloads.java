package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the platform-independent shared-runtime registry payload from a
 * {@link MagicRuntime}. Both the Bukkit and Fabric registries call this so the
 * component/count/platform-label extraction is written once. Platform-specific
 * bits (plugin/mod metadata, whether diagnostics/commands are on) are passed in
 * by the caller, keeping core free of Bukkit/Fabric and diagnostics types.
 */
public final class MagicUtilsConsumerPayloads {
    private MagicUtilsConsumerPayloads() {
    }

    /**
     * Assembles the registry payload for a consumer runtime. Keys match
     * {@link MagicUtilsConsumerInfo}'s {@code KEY_*} constants so the bundle side
     * can decode it with {@link MagicUtilsConsumerInfo#fromPayload}.
     */
    public static Map<String, Object> runtimePayload(
            MagicRuntime runtime,
            String pluginName,
            String version,
            String mainClass,
            @Nullable String description,
            @Nullable String website,
            List<String> authors,
            boolean commandsEnabled,
            @Nullable String permissionPrefix,
            int rootCommandCount,
            boolean diagnosticsEnabled,
            Instant connectedAt
    ) {
        Map<Class<?>, Object> typedComponents = runtime.components();
        Map<String, Object> namedComponents = runtime.namedComponents();
        List<String> namedComponentNames = new ArrayList<>(namedComponents.keySet());
        Collections.sort(namedComponentNames, String.CASE_INSENSITIVE_ORDER);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(MagicUtilsConsumerInfo.KEY_PLUGIN_NAME, pluginName);
        payload.put(MagicUtilsConsumerInfo.KEY_VERSION, version);
        payload.put(MagicUtilsConsumerInfo.KEY_MAIN_CLASS, mainClass);
        payload.put(MagicUtilsConsumerInfo.KEY_DESCRIPTION, description);
        payload.put(MagicUtilsConsumerInfo.KEY_WEBSITE, website);
        payload.put(MagicUtilsConsumerInfo.KEY_AUTHORS, List.copyOf(authors));
        payload.put(MagicUtilsConsumerInfo.KEY_PLATFORM_TYPE, platformTypeLabel(runtime));
        payload.put(MagicUtilsConsumerInfo.KEY_COMMANDS_ENABLED, commandsEnabled);
        payload.put(MagicUtilsConsumerInfo.KEY_PERMISSION_PREFIX, permissionPrefix);
        payload.put(MagicUtilsConsumerInfo.KEY_ROOT_COMMAND_COUNT, rootCommandCount);
        payload.put(MagicUtilsConsumerInfo.KEY_DIAGNOSTICS_ENABLED, diagnosticsEnabled);
        payload.put(MagicUtilsConsumerInfo.KEY_CLOSED, runtime.isClosed());
        payload.put(MagicUtilsConsumerInfo.KEY_TYPED_COMPONENT_COUNT, typedComponents.size());
        payload.put(MagicUtilsConsumerInfo.KEY_NAMED_COMPONENT_COUNT, namedComponents.size());
        payload.put(MagicUtilsConsumerInfo.KEY_NAMED_COMPONENT_NAMES, List.copyOf(namedComponentNames));
        payload.put(MagicUtilsConsumerInfo.KEY_CONNECTED_AT_EPOCH_MILLIS, connectedAt.toEpochMilli());
        return payload;
    }

    /** Human-readable adapter label derived from the runtime's platform class. */
    public static String platformTypeLabel(MagicRuntime runtime) {
        String simpleName = runtime.platform().getClass().getSimpleName();
        if (simpleName.endsWith("PlatformProvider")) {
            simpleName = simpleName.substring(0, simpleName.length() - "PlatformProvider".length());
        }
        if (simpleName.endsWith("Platform")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Platform".length());
        }
        return simpleName;
    }
}
