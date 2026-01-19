package dev.ua.theroer.magicutils.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Internal listener for handling GUI events.
 */
class MagicGuiListener implements Listener {
    private static final Map<Plugin, MagicGuiListener> LISTENERS = new ConcurrentHashMap<>();

    private final Logger logger;
    private final Map<Player, MagicGui> openGuis = new ConcurrentHashMap<>();

    private MagicGuiListener(Plugin plugin) {
        this.logger = plugin != null ? plugin.getLogger() : null;
    }

    /**
     * Ensure the listener is registered with the given plugin.
     *
     * @param plugin plugin to register with
     */
    public static void ensureRegistered(JavaPlugin plugin) {
        register(plugin);
    }

    /**
     * Register the listener with a specific plugin.
     *
     * @param plugin plugin to register with
     */
    public static void register(Plugin plugin) {
        resolveListener(plugin);
    }

    /**
     * Clears tracked GUIs and unregisters all listeners.
     */
    public static void shutdown() {
        for (Plugin plugin : new ArrayList<>(LISTENERS.keySet())) {
            shutdown(plugin);
        }
    }

    /**
     * Clears tracked GUIs and unregisters the listener for a plugin.
     *
     * @param plugin plugin instance
     */
    public static void shutdown(Plugin plugin) {
        MagicGuiListener listener = plugin != null ? LISTENERS.remove(plugin) : null;
        if (listener != null) {
            listener.openGuis.clear();
            HandlerList.unregisterAll(listener);
        }
    }

    /**
     * Register a GUI for a player.
     *
     * @param plugin plugin instance
     * @param player player
     * @param gui    GUI instance
     */
    public static void registerGui(Plugin plugin, Player player, MagicGui gui) {
        MagicGuiListener listener = resolveListener(plugin);
        if (listener == null || player == null || gui == null) {
            return;
        }
        if (listener.logger != null) {
            listener.logger.fine("[MagicGuiListener] Registering GUI for player " + player.getName());
        }
        listener.openGuis.put(player, gui);
    }

    /**
     * Unregister a GUI for a player.
     *
     * @param plugin plugin instance
     * @param player player
     */
    public static void unregisterGui(Plugin plugin, Player player) {
        MagicGuiListener listener = plugin != null ? LISTENERS.get(plugin) : null;
        if (listener == null || player == null) {
            return;
        }
        listener.openGuis.remove(player);
    }

    private static MagicGuiListener resolveListener(Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        return LISTENERS.computeIfAbsent(plugin, key -> {
            MagicGuiListener listener = new MagicGuiListener(key);
            Bukkit.getPluginManager().registerEvents(listener, key);
            return listener;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (logger != null) {
            logger.fine("[MagicGuiListener] Click event for player " + player.getName() +
                    ", openGuis contains: " + openGuis.containsKey(player));
        }

        MagicGui gui = openGuis.get(player);
        if (gui != null) {
            if (logger != null) {
                logger.fine("[MagicGuiListener] GUI found, checking inventory match");
            }
            if (event.getInventory().equals(gui.getInventory())) {
                if (logger != null) {
                    logger.fine("[MagicGuiListener] Inventory matches, calling handleClick");
                }
                gui.handleClick(event);
            } else {
                if (logger != null) {
                    logger.fine("[MagicGuiListener] Inventory does not match");
                }
            }
        } else {
            if (logger != null) {
                logger.fine("[MagicGuiListener] No GUI found for player");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        MagicGui gui = openGuis.get(player);
        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            gui.handleClose(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MagicGui gui = openGuis.get(player);
        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            gui.handleDrag(event);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MagicGui gui = openGuis.get(player);
        if (gui != null) {
            // Close GUI and clean up
            player.closeInventory();
            openGuis.remove(player);
        }
    }
}
