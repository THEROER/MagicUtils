package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MagicSenderAdapters {
    private static final Map<String, MagicSenderAdapter> ADAPTERS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<MagicSenderAdapter> ORDER = new CopyOnWriteArrayList<>();

    private MagicSenderAdapters() {
    }

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

    public static boolean hasPermission(Object sender, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        MagicSender wrapped = wrap(sender);
        return wrapped != null && wrapped.hasPermission(permission);
    }
}
