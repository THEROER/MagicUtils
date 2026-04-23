package dev.ua.theroer.magicutils.bukkit;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.bootstrap.BukkitBootstrap;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitMagicUtilsConsumerRegistry;
import dev.ua.theroer.magicutils.platform.bukkit.BukkitMagicUtilsConsumerRegistry.ConsumerInfo;
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
    private final Map<String, ConsumerInfo> sharedRuntimeConsumers = new ConcurrentHashMap<>();

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
                .permissionPrefix("magicutils")
                .buildRuntime();
        runtime = bootstrap.runtime();

        Logger logger = bootstrap.logger();
        CommandRegistry commandRegistry = bootstrap.commandRegistry();
        if (commandRegistry != null) {
            commandRegistry.registerCommand(new MagicUtilsBundleCommand(this, logger != null ? logger.getCore() : null));
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
        ConsumerInfo consumerInfo = BukkitMagicUtilsConsumerRegistry.consumerInfo(plugin, payload);
        sharedRuntimeConsumers.put(normalizeKey(consumerInfo.pluginName()), consumerInfo);
    }

    public void unregisterSharedRuntimeConsumer(JavaPlugin plugin) {
        if (plugin == null || plugin == this) {
            return;
        }
        sharedRuntimeConsumers.remove(normalizeKey(plugin.getName()));
    }

    public List<ConsumerInfo> snapshotSharedRuntimeConsumers() {
        List<ConsumerInfo> consumers = new ArrayList<>(sharedRuntimeConsumers.values());
        consumers.sort(Comparator.comparing(ConsumerInfo::pluginName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(consumers);
    }

    public @Nullable ConsumerInfo findSharedRuntimeConsumer(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return null;
        }
        return sharedRuntimeConsumers.get(normalizeKey(pluginName));
    }

    private static String normalizeKey(String pluginName) {
        return pluginName.trim().toLowerCase(Locale.ROOT);
    }
}
