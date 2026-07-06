package dev.ua.theroer.magicutils.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Process-wide registry of mods/plugins currently using the shared MagicUtils
 * runtime. On loaders where every consumer shares one classloader (Fabric,
 * NeoForge) this is a plain static holder: the platform bootstrap registers each
 * consuming mod here on startup, and the standalone bundle command reads
 * snapshots from it. Bukkit keeps its own registry (separate plugin classloaders
 * + a reflective bridge to the bundle plugin), but shares the {@link
 * MagicUtilsConsumerInfo} snapshot type.
 *
 * <p>This is the single implementation the per-loader registries delegate to, so
 * the holder logic is not duplicated across platforms.</p>
 */
public final class MagicUtilsConsumerRegistry {
    private static final Map<String, MagicUtilsConsumerInfo> CONSUMERS = new ConcurrentHashMap<>();
    private static final String BUNDLE_NAME = "MagicUtils";

    private MagicUtilsConsumerRegistry() {
    }

    /** Registers or refreshes {@code info} for its mod, keyed by name (case-insensitive). */
    public static void register(MagicUtilsConsumerInfo info) {
        if (info == null || BUNDLE_NAME.equalsIgnoreCase(info.pluginName().trim())) {
            return;
        }
        CONSUMERS.put(normalizeKey(info.pluginName()), info);
    }

    /** Removes the consumer registered under {@code modName}, if any. */
    public static void unregister(@Nullable String modName) {
        if (modName == null || modName.isBlank()) {
            return;
        }
        CONSUMERS.remove(normalizeKey(modName));
    }

    /** Immutable, name-sorted snapshot of the registered consumers. */
    public static List<MagicUtilsConsumerInfo> snapshot() {
        List<MagicUtilsConsumerInfo> consumers = new ArrayList<>(CONSUMERS.values());
        consumers.sort(Comparator.comparing(MagicUtilsConsumerInfo::pluginName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(consumers);
    }

    /** Consumer matching {@code modName} ignoring case, or null. */
    public static @Nullable MagicUtilsConsumerInfo find(@Nullable String modName) {
        if (modName == null || modName.isBlank()) {
            return null;
        }
        return CONSUMERS.get(normalizeKey(modName));
    }

    private static String normalizeKey(String modName) {
        return modName.trim().toLowerCase(Locale.ROOT);
    }
}
