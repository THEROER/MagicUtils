package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.bootstrap.MagicUtilsConsumerPayloads;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks Bukkit/Paper plugins that are currently using the shared MagicUtils
 * runtime. The registry snapshot type ({@link MagicUtilsConsumerInfo}) and the
 * payload encode/decode helpers are shared with the Fabric registry
 * (platform-api + core); only the Bukkit plugin lookup and the reflective bridge
 * to the bundle plugin live here.
 */
public final class BukkitMagicUtilsConsumerRegistry {
    private static final String BUNDLE_PLUGIN_NAME = "MagicUtils";
    private static final String REGISTER_METHOD = "registerSharedRuntimeConsumer";
    private static final String UNREGISTER_METHOD = "unregisterSharedRuntimeConsumer";

    private BukkitMagicUtilsConsumerRegistry() {
    }

    /**
     * Registers or refreshes a plugin using the shared MagicUtils runtime.
     *
     * @param plugin plugin instance
     * @param runtime runtime container
     * @param commandRegistry command registry when commands are enabled
     */
    public static void register(JavaPlugin plugin, MagicRuntime runtime, @Nullable CommandRegistry commandRegistry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runtime, "runtime");
        if (isBundlePlugin(plugin)) {
            return;
        }

        JavaPlugin bundlePlugin = resolveBundlePlugin(plugin);
        if (bundlePlugin == null || bundlePlugin == plugin) {
            return;
        }

        try {
            invokeBundleMethod(
                    bundlePlugin,
                    REGISTER_METHOD,
                    new Class<?>[]{JavaPlugin.class, Map.class},
                    plugin,
                    snapshotPayload(plugin, runtime, commandRegistry, Instant.now())
            );
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Failed to register shared MagicUtils consumer: " + exception.getMessage());
        }
    }

    /**
     * Removes a plugin from the shared runtime registry.
     *
     * @param plugin plugin instance
     */
    public static void unregister(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        if (isBundlePlugin(plugin)) {
            return;
        }

        JavaPlugin bundlePlugin = resolveBundlePlugin(plugin);
        if (bundlePlugin == null || bundlePlugin == plugin) {
            return;
        }

        try {
            invokeBundleMethod(bundlePlugin, UNREGISTER_METHOD, new Class<?>[]{JavaPlugin.class}, plugin);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Failed to unregister shared MagicUtils consumer: " + exception.getMessage());
        }
    }

    /**
     * Rebuilds an immutable consumer snapshot from a bridge payload, filling
     * gaps from the plugin's own metadata.
     *
     * @param plugin plugin instance
     * @param payload registry payload emitted by the consumer runtime
     * @return immutable consumer info
     */
    public static MagicUtilsConsumerInfo consumerInfo(JavaPlugin plugin, Map<String, Object> payload) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(payload, "payload");
        var pluginMeta = plugin.getPluginMeta();
        return MagicUtilsConsumerInfo.fromPayload(
                payload,
                plugin.getName(),
                pluginMeta.getVersion(),
                pluginMeta.getMainClass(),
                pluginMeta.getDescription(),
                pluginMeta.getWebsite(),
                List.copyOf(pluginMeta.getAuthors())
        );
    }

    private static boolean isBundlePlugin(JavaPlugin plugin) {
        return BUNDLE_PLUGIN_NAME.equalsIgnoreCase(plugin.getName());
    }

    private static @Nullable JavaPlugin resolveBundlePlugin(JavaPlugin plugin) {
        Plugin bundlePlugin = plugin.getServer().getPluginManager().getPlugin(BUNDLE_PLUGIN_NAME);
        return bundlePlugin instanceof JavaPlugin javaPlugin ? javaPlugin : null;
    }

    private static void invokeBundleMethod(
            JavaPlugin bundlePlugin,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) throws ReflectiveOperationException {
        Method method = bundlePlugin.getClass().getMethod(methodName, parameterTypes);
        method.invoke(bundlePlugin, args);
    }

    private static Map<String, Object> snapshotPayload(
            JavaPlugin plugin,
            MagicRuntime runtime,
            @Nullable CommandRegistry commandRegistry,
            Instant connectedAt
    ) {
        var pluginMeta = plugin.getPluginMeta();
        boolean commandsEnabled = commandRegistry != null;
        int rootCommandCount = commandsEnabled ? commandRegistry.commandManager().getAll().size() : 0;
        String permissionPrefix = commandsEnabled ? commandRegistry.permissionPrefix() : null;
        boolean diagnosticsEnabled = runtime.findComponent(DiagnosticsService.class).isPresent();

        return MagicUtilsConsumerPayloads.runtimePayload(
                runtime,
                plugin.getName(),
                pluginMeta.getVersion(),
                pluginMeta.getMainClass(),
                pluginMeta.getDescription(),
                pluginMeta.getWebsite(),
                List.copyOf(pluginMeta.getAuthors()),
                commandsEnabled,
                permissionPrefix,
                rootCommandCount,
                diagnosticsEnabled,
                connectedAt
        );
    }
}
