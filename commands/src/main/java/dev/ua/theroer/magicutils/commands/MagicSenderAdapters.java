package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for {@link MagicSenderAdapter} instances.
 */
public final class MagicSenderAdapters {
    private static final Map<String, MagicSenderAdapter> ADAPTERS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<MagicSenderAdapter> ORDER = new CopyOnWriteArrayList<>();

    private MagicSenderAdapters() {
    }

    /**
     * Registers an adapter under a stable identifier.
     *
     * @param id adapter id
     * @param adapter adapter instance
     */
    public static void register(String id, MagicSenderAdapter adapter) {
        if (id == null || id.isEmpty() || adapter == null) {
            return;
        }
        MagicSenderAdapter previous = ADAPTERS.put(id, adapter);
        if (previous != null) {
            ORDER.remove(previous);
        }
        ORDER.addIfAbsent(adapter);
    }

    /**
     * Unregisters an adapter by identifier.
     *
     * @param id adapter id
     * @return true if an adapter was removed
     */
    public static boolean unregister(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        MagicSenderAdapter adapter = ADAPTERS.remove(id);
        if (adapter != null) {
            ORDER.remove(adapter);
            return true;
        }
        return false;
    }

    /**
     * Clears all registered adapters.
     */
    public static void clear() {
        ADAPTERS.clear();
        ORDER.clear();
    }

    /**
     * Attempts to wrap a raw sender using registered adapters.
     *
     * @param sender raw sender instance
     * @return wrapped sender or null if unsupported
     */
    public static @Nullable MagicSender wrap(Object sender) {
        if (sender == null) {
            return null;
        }
        if (sender instanceof MagicSender magicSender) {
            return magicSender;
        }
        for (MagicSenderAdapter adapter : ORDER) {
            if (adapter == null || !adapter.supports(sender)) {
                continue;
            }
            MagicSender wrapped = adapter.wrap(sender);
            if (wrapped != null) {
                return wrapped;
            }
        }
        return null;
    }

    /**
     * Checks permission for a raw sender using registered adapters.
     *
     * @param sender raw sender instance
     * @param permission permission node
     * @return true if granted
     */
    public static boolean hasPermission(Object sender, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        MagicSender wrapped = wrap(sender);
        return wrapped != null && wrapped.hasPermission(permission);
    }
}
