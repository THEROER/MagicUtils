package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerRegistry;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric-facing view of the shared-runtime consumer registry. Fabric mods share
 * one classloader, so the registry is just the common {@link
 * MagicUtilsConsumerRegistry}; this class stays as a thin, stable entry point for
 * {@code FabricBootstrap} and keeps the holder logic un-duplicated.
 */
public final class MagicUtilsFabricConsumerRegistry {
    private MagicUtilsFabricConsumerRegistry() {
    }

    /** @see MagicUtilsConsumerRegistry#register(MagicUtilsConsumerInfo) */
    public static void register(MagicUtilsConsumerInfo info) {
        MagicUtilsConsumerRegistry.register(info);
    }

    /** @see MagicUtilsConsumerRegistry#unregister(String) */
    public static void unregister(@Nullable String modName) {
        MagicUtilsConsumerRegistry.unregister(modName);
    }

    /** @see MagicUtilsConsumerRegistry#snapshot() */
    public static List<MagicUtilsConsumerInfo> snapshot() {
        return MagicUtilsConsumerRegistry.snapshot();
    }

    /** @see MagicUtilsConsumerRegistry#find(String) */
    public static @Nullable MagicUtilsConsumerInfo find(@Nullable String modName) {
        return MagicUtilsConsumerRegistry.find(modName);
    }
}
