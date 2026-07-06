package dev.ua.theroer.magicutils.platform;

import java.util.List;

/**
 * Live view of a shared-runtime consumer's mutable state. Unlike the static
 * metadata captured at registration (name, version, authors, …), these values
 * change as the consumer initializes: commands are registered on server start,
 * components are added while the runtime is built, and the runtime may later be
 * closed. The registry keeps a reference to this view and reads it lazily when a
 * {@link MagicUtilsConsumerInfo} snapshot is requested, so {@code /magicutils
 * mods} always reflects the current state rather than a frozen early snapshot.
 *
 * <p>On loaders where every consumer shares one classloader (Fabric, NeoForge)
 * the view simply closes over the consumer's {@code MagicRuntime} and command
 * registry. On Bukkit, where the bundle plugin and consumers live in separate
 * classloaders, the reflective bridge supplies an equivalent view that rebuilds
 * the payload on demand.</p>
 */
public interface MagicUtilsConsumerRuntimeView {
    /** Number of registered root commands right now (0 when commands are disabled). */
    int rootCommandCount();

    /** Typed component count in the runtime right now. */
    int typedComponentCount();

    /** Named component count in the runtime right now. */
    int namedComponentCount();

    /** Named component keys right now (order-independent; callers sort for display). */
    List<String> namedComponentNames();

    /** Whether the diagnostics service is present right now. */
    boolean diagnosticsEnabled();

    /** Whether the runtime is closed right now. */
    boolean closed();
}
