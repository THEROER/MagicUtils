package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.ConfigNamespaceProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/**
 * Platform provider for Fabric runtime.
 */
public final class FabricPlatformProvider implements Platform, ConfigNamespaceProvider, ConfigFormatProvider {
    private final Supplier<MinecraftServer> serverSupplier;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final Path configDir;
    private final boolean useConfigNamespace;
    private final String configNamespace;

    public FabricPlatformProvider(MinecraftServer server) {
        this(() -> server, LoggerFactory.getLogger("MagicUtils-Fabric"));
    }

    public FabricPlatformProvider(Supplier<MinecraftServer> serverSupplier) {
        this(serverSupplier, LoggerFactory.getLogger("MagicUtils-Fabric"));
    }

    public FabricPlatformProvider(MinecraftServer server, Logger slf4j) {
        this(() -> server, slf4j, resolveConfigDir());
    }

    public FabricPlatformProvider(Supplier<MinecraftServer> serverSupplier, Logger slf4j) {
        this(serverSupplier, slf4j, resolveConfigDir());
    }

    public FabricPlatformProvider(Supplier<MinecraftServer> serverSupplier, Logger slf4j, Path configDir) {
        Logger effective = slf4j != null ? slf4j : LoggerFactory.getLogger("MagicUtils-Fabric");
        this.serverSupplier = serverSupplier != null ? serverSupplier : () -> null;
        this.logger = new FabricPlatformLogger(effective);
        this.consoleAudience = new FabricPlainConsoleAudience(this.logger);
        Path resolvedConfig = configDir != null ? configDir : Path.of("config");
        this.configDir = resolvedConfig;
        this.useConfigNamespace = isDefaultConfigDir(resolvedConfig);
        this.configNamespace = effective.getName();
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public String resolveConfigNamespace(String pluginName) {
        if (!useConfigNamespace) {
            return null;
        }
        if (pluginName != null && !pluginName.trim().isEmpty()) {
            return pluginName;
        }
        return configNamespace;
    }

    @Override
    public String defaultConfigExtension() {
        return "jsonc";
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    @Override
    public Audience console() {
        return consoleAudience;
    }

    @Override
    public Collection<Audience> onlinePlayers() {
        MinecraftServer server = server();
        if (server == null) {
            return Collections.emptyList();
        }
        return server.getPlayerManager().getPlayerList().stream()
                .map(this::wrap)
                .collect(Collectors.toList());
    }

    @Override
    public void runOnMain(Runnable task) {
        if (task == null) {
            return;
        }
        MinecraftServer server = server();
        if (server == null || server.isOnThread()) {
            task.run();
            return;
        }
        server.execute(task);
    }

    @Override
    public boolean isMainThread() {
        MinecraftServer server = server();
        return server == null || server.isOnThread();
    }

    private Audience wrap(ServerPlayerEntity player) {
        return new FabricAudience(player);
    }

    private MinecraftServer server() {
        return serverSupplier != null ? serverSupplier.get() : null;
    }

    private static Path resolveConfigDir() {
        try {
            return FabricLoader.getInstance().getConfigDir();
        } catch (Throwable ignored) {
            return Path.of("config");
        }
    }

    private static boolean isDefaultConfigDir(Path configDir) {
        try {
            Path resolved = configDir != null ? configDir : Path.of("config");
            Path left = resolved.toAbsolutePath().normalize();
            Path right = resolveConfigDir().toAbsolutePath().normalize();
            return left.equals(right);
        } catch (Exception e) {
            return false;
        }
    }
}
