package dev.ua.theroer.magicutils.platform.velocity;

import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRuntimeView;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Velocity-facing view of the shared-runtime consumer registry. Velocity plugins
 * share one classloader on the proxy, so the registry is just the common {@link
 * MagicUtilsConsumerRegistry}; this class stays as a thin, stable entry point for
 * the {@code velocity-bundle} plugin and downstream consumers, mirroring the
 * Fabric registry so {@code /magicutils mods} reads consumers live.
 */
public final class VelocityMagicUtilsConsumerRegistry {
    private VelocityMagicUtilsConsumerRegistry() {
    }

    /** @see MagicUtilsConsumerRegistry#register(MagicUtilsConsumerInfo) */
    public static void register(MagicUtilsConsumerInfo info) {
        MagicUtilsConsumerRegistry.register(info);
    }

    /** @see MagicUtilsConsumerRegistry#register(MagicUtilsConsumerRegistry.StaticMeta, MagicUtilsConsumerRuntimeView) */
    public static void register(MagicUtilsConsumerRegistry.StaticMeta meta, MagicUtilsConsumerRuntimeView view) {
        MagicUtilsConsumerRegistry.register(meta, view);
    }

    /** @see MagicUtilsConsumerRegistry#unregister(String) */
    public static void unregister(@Nullable String pluginName) {
        MagicUtilsConsumerRegistry.unregister(pluginName);
    }

    /** @see MagicUtilsConsumerRegistry#snapshot() */
    public static List<MagicUtilsConsumerInfo> snapshot() {
        return MagicUtilsConsumerRegistry.snapshot();
    }

    /** @see MagicUtilsConsumerRegistry#find(String) */
    public static @Nullable MagicUtilsConsumerInfo find(@Nullable String pluginName) {
        return MagicUtilsConsumerRegistry.find(pluginName);
    }
}
