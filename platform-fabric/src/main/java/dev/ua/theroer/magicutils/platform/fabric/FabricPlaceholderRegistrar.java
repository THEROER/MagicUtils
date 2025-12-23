package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FabricPlaceholderRegistrar implements MagicPlaceholders.PlaceholderListener {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private final LoggerCore logger;
    private final FabricPlaceholderBackend pb4Backend;
    private final FabricPlaceholderBackend miniBackend;

    private FabricPlaceholderRegistrar(LoggerCore logger) {
        this.logger = logger;
        this.pb4Backend = createBackend(
                "eu.pb4.placeholders.api.Placeholders",
                "dev.ua.theroer.magicutils.platform.fabric.Pb4PlaceholderBackend"
        );
        this.miniBackend = createBackend(
                "io.github.miniplaceholders.api.MiniPlaceholders",
                "dev.ua.theroer.magicutils.platform.fabric.MiniPlaceholdersBackend"
        );
    }

    public static void install(LoggerCore logger) {
        if (logger == null) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        FabricPlaceholderRegistrar registrar = new FabricPlaceholderRegistrar(logger);
        MagicPlaceholders.addListener(registrar);
        registrar.registerExisting();
    }

    @Override
    public void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key) {
        runOnMain(() -> {
            if (pb4Backend != null) {
                pb4Backend.register(key);
            }
            if (miniBackend != null) {
                miniBackend.register(key);
            }
        });
    }

    @Override
    public void onPlaceholderUnregistered(MagicPlaceholders.PlaceholderKey key) {
        runOnMain(() -> {
            if (pb4Backend != null) {
                pb4Backend.unregister(key);
            }
            if (miniBackend != null) {
                miniBackend.unregister(key);
            }
        });
    }

    @Override
    public void onNamespaceUpdated(String namespace) {
        runOnMain(() -> {
            if (miniBackend != null) {
                miniBackend.updateNamespace(namespace);
            }
        });
    }

    private void registerExisting() {
        runOnMain(() -> {
            if (pb4Backend != null) {
                pb4Backend.registerAll();
            }
            if (miniBackend != null) {
                miniBackend.registerAll();
            }
        });
    }

    private void runOnMain(Runnable task) {
        if (logger == null || task == null) {
            return;
        }
        logger.getPlatform().runOnMain(task);
    }

    private FabricPlaceholderBackend createBackend(String probeClass, String backendClass) {
        try {
            Class.forName(probeClass);
            Class<?> backend = Class.forName(backendClass);
            return (FabricPlaceholderBackend) backend.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
