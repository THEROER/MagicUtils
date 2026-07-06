package dev.ua.theroer.magicutils.platform;

import java.time.Instant;
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
 * <p>Registrations keep a live {@link MagicUtilsConsumerRuntimeView} rather than a
 * frozen {@link MagicUtilsConsumerInfo}. The consumer's command/component counts
 * change after registration (commands are wired on server start, components while
 * the runtime is built), so {@link #snapshot()} / {@link #find(String)} rebuild a
 * fresh {@code MagicUtilsConsumerInfo} from the view at call time. This keeps
 * {@code /magicutils mods} honest instead of showing an early snapshot.</p>
 *
 * <p>This is the single implementation the per-loader registries delegate to, so
 * the holder logic is not duplicated across platforms.</p>
 */
public final class MagicUtilsConsumerRegistry {
    private static final Map<String, Registration> CONSUMERS = new ConcurrentHashMap<>();
    private static final String BUNDLE_NAME = "MagicUtils";

    private MagicUtilsConsumerRegistry() {
    }

    /**
     * Immutable static metadata of a consumer, captured once at registration.
     * The mutable state (commands, components, diagnostics, closed) is read
     * separately from a {@link MagicUtilsConsumerRuntimeView}.
     */
    public record StaticMeta(
            String pluginName,
            String version,
            String mainClass,
            @Nullable String description,
            @Nullable String website,
            List<String> authors,
            String platformType,
            boolean commandsEnabled,
            @Nullable String permissionPrefix,
            Instant connectedAt
    ) {
        public StaticMeta {
            authors = List.copyOf(authors);
        }
    }

    private record Registration(StaticMeta meta, MagicUtilsConsumerRuntimeView view) {
        MagicUtilsConsumerInfo toInfo() {
            return MagicUtilsConsumerInfo.fromStatic(
                    meta.pluginName(),
                    meta.version(),
                    meta.mainClass(),
                    meta.description(),
                    meta.website(),
                    meta.authors(),
                    meta.platformType(),
                    meta.commandsEnabled(),
                    meta.permissionPrefix(),
                    meta.connectedAt(),
                    view);
        }
    }

    /**
     * Registers or refreshes a consumer from its static {@code meta} plus a live
     * {@code view}. The view is read lazily on every {@link #snapshot()} /
     * {@link #find(String)}, so callers should pass a view backed by the live
     * runtime rather than a captured snapshot.
     */
    public static void register(StaticMeta meta, MagicUtilsConsumerRuntimeView view) {
        if (meta == null || view == null || BUNDLE_NAME.equalsIgnoreCase(meta.pluginName().trim())) {
            return;
        }
        CONSUMERS.put(normalizeKey(meta.pluginName()), new Registration(meta, view));
    }

    /**
     * Registers or refreshes a consumer from an already-built {@code info}. The
     * dynamic fields are frozen at the value they held in {@code info}; prefer
     * {@link #register(StaticMeta, MagicUtilsConsumerRuntimeView)} where a live
     * view is available. Kept for bridges that can only produce a snapshot.
     */
    public static void register(MagicUtilsConsumerInfo info) {
        if (info == null || BUNDLE_NAME.equalsIgnoreCase(info.pluginName().trim())) {
            return;
        }
        StaticMeta meta = new StaticMeta(
                info.pluginName(),
                info.version(),
                info.mainClass(),
                info.description(),
                info.website(),
                info.authors(),
                info.platformType(),
                info.commandsEnabled(),
                info.permissionPrefix(),
                info.connectedAt());
        CONSUMERS.put(normalizeKey(info.pluginName()), new Registration(meta, frozenView(info)));
    }

    /** Removes the consumer registered under {@code modName}, if any. */
    public static void unregister(@Nullable String modName) {
        if (modName == null || modName.isBlank()) {
            return;
        }
        CONSUMERS.remove(normalizeKey(modName));
    }

    /** Immutable, name-sorted snapshot of the registered consumers, rebuilt from live views. */
    public static List<MagicUtilsConsumerInfo> snapshot() {
        List<MagicUtilsConsumerInfo> consumers = new ArrayList<>(CONSUMERS.size());
        for (Registration registration : CONSUMERS.values()) {
            consumers.add(registration.toInfo());
        }
        consumers.sort(Comparator.comparing(MagicUtilsConsumerInfo::pluginName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(consumers);
    }

    /** Consumer matching {@code modName} ignoring case, rebuilt from its live view, or null. */
    public static @Nullable MagicUtilsConsumerInfo find(@Nullable String modName) {
        if (modName == null || modName.isBlank()) {
            return null;
        }
        Registration registration = CONSUMERS.get(normalizeKey(modName));
        return registration != null ? registration.toInfo() : null;
    }

    private static String normalizeKey(String modName) {
        return modName.trim().toLowerCase(Locale.ROOT);
    }

    /** A view returning the dynamic fields frozen at the value held by {@code info}. */
    private static MagicUtilsConsumerRuntimeView frozenView(MagicUtilsConsumerInfo info) {
        return new MagicUtilsConsumerRuntimeView() {
            @Override
            public int rootCommandCount() {
                return info.rootCommandCount();
            }

            @Override
            public int typedComponentCount() {
                return info.typedComponentCount();
            }

            @Override
            public int namedComponentCount() {
                return info.namedComponentCount();
            }

            @Override
            public List<String> namedComponentNames() {
                return info.namedComponentNames();
            }

            @Override
            public boolean diagnosticsEnabled() {
                return info.diagnosticsEnabled();
            }

            @Override
            public boolean closed() {
                return info.closed();
            }
        };
    }
}
