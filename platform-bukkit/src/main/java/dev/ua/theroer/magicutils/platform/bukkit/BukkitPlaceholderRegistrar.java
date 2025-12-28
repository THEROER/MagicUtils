package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers MagicPlaceholders with Bukkit placeholder backend when available.
 */
public final class BukkitPlaceholderRegistrar implements MagicPlaceholders.PlaceholderListener {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private final BukkitPlaceholderBackend backend;

    private BukkitPlaceholderRegistrar(JavaPlugin plugin) {
        this.backend = createBackend(plugin);
    }

    /**
     * Installs the registrar for the plugin.
     *
     * @param plugin owning plugin
     */
    public static void install(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        BukkitPlaceholderRegistrar registrar = new BukkitPlaceholderRegistrar(plugin);
        if (registrar.backend == null) {
            return;
        }
        MagicPlaceholders.addListener(registrar);
        registrar.backend.registerAll();
    }

    @Override
    public void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key) {
        if (backend != null) {
            backend.onPlaceholderRegistered(key);
        }
    }

    @Override
    public void onPlaceholderUnregistered(MagicPlaceholders.PlaceholderKey key) {
        if (backend != null) {
            backend.ensureNamespace(key.namespace());
        }
    }

    @Override
    public void onNamespaceUpdated(String namespace) {
        if (backend != null) {
            backend.ensureNamespace(namespace);
        }
    }

    private BukkitPlaceholderBackend createBackend(JavaPlugin plugin) {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        } catch (Throwable ignored) {
            return null;
        }
        try {
            Class<?> impl = Class.forName("dev.ua.theroer.magicutils.platform.bukkit.PapiPlaceholderBackend");
            return (BukkitPlaceholderBackend) impl.getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
