package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.AudienceResolver;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycle;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleListener;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleType;
import dev.ua.theroer.magicutils.platform.PlayerLocale;
import dev.ua.theroer.magicutils.platform.PlayerLocaleListener;
import dev.ua.theroer.magicutils.platform.PlayerMessage;
import dev.ua.theroer.magicutils.platform.PlayerMessageListener;
import dev.ua.theroer.magicutils.platform.PlayerMessageType;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.platform.TaskSchedulers;
import dev.ua.theroer.magicutils.config.adapters.AdaptersBootstrap;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Platform provider for Bukkit/Paper runtime.
 */
public class BukkitPlatformProvider implements Platform, ShutdownHookRegistrar {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final PlatformLogger logger;
    private final Audience consoleAudience;
    private final BukkitScheduler scheduler;
    private final List<Runnable> shutdownHooks = new CopyOnWriteArrayList<>();
    private final List<PlayerLifecycleListener> playerLifecycleListeners = new CopyOnWriteArrayList<>();
    private final List<PlayerLocaleListener> playerLocaleListeners = new CopyOnWriteArrayList<>();
    private final List<PlayerMessageListener> playerMessageListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean shutdownListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean playerLifecycleListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean playerLocaleListenerRegistered = new AtomicBoolean(false);
    private final AtomicBoolean playerMessageListenerRegistered = new AtomicBoolean(false);
    private final TaskScheduler taskScheduler;

    /**
     * Create a Bukkit/Paper platform adapter around the given plugin.
     *
     * @param plugin owning plugin instance
     */
    public BukkitPlatformProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = new BukkitPlatformLogger(plugin.getLogger());
        this.scheduler = Bukkit.getScheduler();
        this.consoleAudience = new BukkitConsoleAudience(plugin.getLogger(), plugin.getName());
        TaskScheduler scheduler = TaskSchedulers.create("MagicUtils-" + plugin.getName(), null);
        this.taskScheduler = scheduler;
        registerShutdownHookInternal(this.plugin, this.logger, this.shutdownHooks, this.shutdownListenerRegistered,
                scheduler::shutdown);
        AdaptersBootstrap.registerDefaults();
        AudienceResolver.registerFactory(obj -> obj instanceof CommandSender sender ? wrap(sender) : null);
    }

    @Override
    public Path configDir() {
        return plugin.getDataFolder().toPath();
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
        return Bukkit.getOnlinePlayers().stream()
                .map(this::wrap)
                .collect(Collectors.toList());
    }

    @Override
    public void runOnMain(Runnable task) {
        if (isMainThread()) {
            task.run();
            return;
        }
        scheduler.runTask(plugin, task);
    }

    @Override
    public void runForAudience(Audience audience, Runnable task) {
        if (task == null) {
            return;
        }
        if (audience != null && audience.id() != null) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(audience.id());
            if (player != null) {
                BukkitThreading.runEntity(plugin, player, task);
                return;
            }
        }
        runOnMain(task);
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public TaskScheduler scheduler() {
        return taskScheduler;
    }

    @Override
    public void registerShutdownHook(Runnable hook) {
        registerShutdownHookInternal(plugin, logger, shutdownHooks, shutdownListenerRegistered, hook);
    }

    @Override
    public void unregisterShutdownHook(Runnable hook) {
        if (hook == null) {
            return;
        }
        shutdownHooks.remove(hook);
    }

    @Override
    public ListenerSubscription subscribePlayerMessages(PlayerMessageListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        playerMessageListeners.add(listener);
        registerPlayerMessageHook();
        return () -> playerMessageListeners.remove(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        playerLifecycleListeners.add(listener);
        registerPlayerLifecycleHook();
        return () -> playerLifecycleListeners.remove(listener);
    }

    @Override
    public ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
        if (listener == null) {
            return ListenerSubscription.noop();
        }
        playerLocaleListeners.add(listener);
        registerPlayerLocaleHook();
        publishCurrentPlayerLocales(listener);
        return () -> playerLocaleListeners.remove(listener);
    }

    private Audience wrap(CommandSender sender) {
        return new BukkitAudienceWrapper(plugin, sender);
    }

    private void registerPlayerMessageHook() {
        if (!playerMessageListenerRegistered.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onChat(AsyncChatEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                publishPlayerMessage(new PlayerMessage(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getName(),
                        PLAIN.serialize(event.message()),
                        PlayerMessageType.CHAT
                ));
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onCommand(PlayerCommandPreprocessEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                publishPlayerMessage(new PlayerMessage(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getName(),
                        event.getMessage(),
                        PlayerMessageType.COMMAND
                ));
            }
        }, plugin);
    }

    private void registerPlayerLifecycleHook() {
        if (!playerLifecycleListenerRegistered.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onJoin(PlayerJoinEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                publishPlayerLifecycle(new PlayerLifecycle(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getName(),
                        PlayerLifecycleType.JOIN
                ));
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onQuit(PlayerQuitEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                publishPlayerLifecycle(new PlayerLifecycle(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getName(),
                        PlayerLifecycleType.LEAVE
                ));
            }
        }, plugin);
    }

    private void registerPlayerLocaleHook() {
        if (!playerLocaleListenerRegistered.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onJoin(PlayerJoinEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                publishPlayerLocale(event.getPlayer(), event.getPlayer().getLocale());
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onLocaleChange(PlayerLocaleChangeEvent event) {
                if (event == null || event.getPlayer() == null) {
                    return;
                }
                publishPlayerLocale(event.getPlayer(), event.getLocale());
            }
        }, plugin);
    }

    private void publishPlayerMessage(PlayerMessage message) {
        if (message == null || !message.isValid() || playerMessageListeners.isEmpty()) {
            return;
        }
        for (PlayerMessageListener listener : playerMessageListeners) {
            try {
                listener.onPlayerMessage(message);
            } catch (RuntimeException e) {
                logger.warn("Failed to deliver player message listener", e);
            }
        }
    }

    private void publishPlayerLifecycle(PlayerLifecycle lifecycle) {
        if (lifecycle == null || !lifecycle.isValid() || playerLifecycleListeners.isEmpty()) {
            return;
        }
        for (PlayerLifecycleListener listener : playerLifecycleListeners) {
            try {
                listener.onPlayerLifecycle(lifecycle);
            } catch (RuntimeException e) {
                logger.warn("Failed to deliver player lifecycle listener", e);
            }
        }
    }

    private void publishCurrentPlayerLocales(PlayerLocaleListener listener) {
        if (listener == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            publishPlayerLocale(listener, toPlayerLocale(player, player.getLocale()));
        }
    }

    private void publishPlayerLocale(Player player, String localeTag) {
        publishPlayerLocale(toPlayerLocale(player, localeTag));
    }

    private void publishPlayerLocale(PlayerLocale playerLocale) {
        if (playerLocale == null || !playerLocale.isValid() || playerLocaleListeners.isEmpty()) {
            return;
        }
        for (PlayerLocaleListener listener : playerLocaleListeners) {
            publishPlayerLocale(listener, playerLocale);
        }
    }

    private void publishPlayerLocale(PlayerLocaleListener listener, PlayerLocale playerLocale) {
        if (listener == null || playerLocale == null || !playerLocale.isValid()) {
            return;
        }
        try {
            listener.onPlayerLocale(playerLocale);
        } catch (RuntimeException e) {
            logger.warn("Failed to deliver player locale listener", e);
        }
    }

    private PlayerLocale toPlayerLocale(Player player, String localeTag) {
        if (player == null || localeTag == null || localeTag.isBlank()) {
            return null;
        }
        return new PlayerLocale(player.getUniqueId(), player.getName(), localeTag);
    }

    private static void registerShutdownHookInternal(JavaPlugin plugin,
                                                     PlatformLogger logger,
                                                     List<Runnable> shutdownHooks,
                                                     AtomicBoolean shutdownListenerRegistered,
                                                     Runnable hook) {
        if (hook == null) {
            return;
        }
        shutdownHooks.add(hook);
        if (shutdownListenerRegistered.compareAndSet(false, true)) {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPluginDisable(PluginDisableEvent event) {
                    if (event.getPlugin() != plugin) {
                        return;
                    }
                    for (Runnable runnable : shutdownHooks) {
                        try {
                            runnable.run();
                        } catch (RuntimeException e) {
                            if (logger != null) {
                                logger.warn("Failed to run shutdown hook", e);
                            }
                        }
                    }
                    shutdownHooks.clear();
                }
            }, plugin);
        }
    }
}
