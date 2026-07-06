package dev.ua.theroer.magicutils.bukkit;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.bootstrap.BukkitBootstrap;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsCommandSupport;
import dev.ua.theroer.magicutils.diagnostics.DiagnosticsService;
import dev.ua.theroer.magicutils.diagnostics.MagicUtilsBundleCommand;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitMagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.MagicUtilsConsumerInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Shared MagicUtils runtime plugin for Bukkit/Paper servers.
 */
public final class MagicUtilsBukkitBundlePlugin extends JavaPlugin {
    private MagicRuntime runtime;
    private final Map<String, MagicUtilsConsumerInfo> sharedRuntimeConsumers = new ConcurrentHashMap<>();

    /**
     * Creates a new instance of the MagicUtils bundle plugin.
     * Required by the Bukkit platform to initialize the plugin.
     */
    public MagicUtilsBukkitBundlePlugin() {
    }

    @Override
    public void onEnable() {
        BukkitBootstrap.RuntimeResult bootstrap = BukkitBootstrap.forPlugin(this)
                .initLanguage(false)
                .bindLoggerLanguage(false)
                .setMessagesManager(false)
                .registerMessages(false)
                .addMagicUtilsMessages(false)
                .enableCommands()
                .enableDiagnostics()
                .permissionPrefix("magicutils")
                .buildRuntime();
        runtime = bootstrap.runtime();

        Logger logger = bootstrap.logger();
        CommandRegistry commandRegistry = bootstrap.commandRegistry();
        if (commandRegistry != null) {
            // Register the diagnostics suite-name parser so `/magicutils diagnostics suite <name>` resolves.
            DiagnosticsCommandSupport.registerTypeParsers(commandRegistry.commandManager());
            commandRegistry.registerCommand(new MagicUtilsBundleCommand(
                    logger != null ? logger.getCore() : null,
                    getPluginMeta().getVersion(),
                    this::snapshotSharedRuntimeConsumers,
                    this::findSharedRuntimeConsumer,
                    this::diagnosticsService,
                    commandRegistry::commandManager));
        }
        getLogger().info("MagicUtils Bukkit bundle loaded.");
    }

    @Override
    public void onDisable() {
        sharedRuntimeConsumers.clear();
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    public void registerSharedRuntimeConsumer(JavaPlugin plugin, Map<String, Object> payload) {
        if (plugin == null || plugin == this || payload == null) {
            return;
        }
        MagicUtilsConsumerInfo consumerInfo = BukkitMagicUtilsConsumerRegistry.consumerInfo(plugin, payload);
        sharedRuntimeConsumers.put(normalizeKey(consumerInfo.pluginName()), consumerInfo);
    }

    public void unregisterSharedRuntimeConsumer(JavaPlugin plugin) {
        if (plugin == null || plugin == this) {
            return;
        }
        sharedRuntimeConsumers.remove(normalizeKey(plugin.getName()));
    }

    public List<MagicUtilsConsumerInfo> snapshotSharedRuntimeConsumers() {
        List<MagicUtilsConsumerInfo> consumers = new ArrayList<>(sharedRuntimeConsumers.values());
        consumers.sort(Comparator.comparing(MagicUtilsConsumerInfo::pluginName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(consumers);
    }

    /**
     * Returns the diagnostics service from the shared runtime, or {@code null} when unavailable.
     *
     * @return the diagnostics service or {@code null}
     */
    public @Nullable DiagnosticsService diagnosticsService() {
        MagicRuntime current = runtime;
        if (current == null) {
            return null;
        }
        return current.findComponent(DiagnosticsService.class).orElse(null);
    }

    public @Nullable MagicUtilsConsumerInfo findSharedRuntimeConsumer(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return null;
        }
        return sharedRuntimeConsumers.get(normalizeKey(pluginName));
    }

    private static String normalizeKey(String pluginName) {
        return pluginName.trim().toLowerCase(Locale.ROOT);
    }
}
