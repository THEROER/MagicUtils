package dev.ua.theroer.magicutils.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal listener for handling GUI events.
 */
class MagicGuiListener implements Listener {
    private static final PrefixedLogger logger = Logger.create("MagicGui", "[GUI]");
    private static boolean registered = false;
    private static final Map<Player, MagicGui> openGuis = new HashMap<>();
    private static Plugin registeredPlugin = null;

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
        if (!registered || registeredPlugin != plugin) {
            if (registered && registeredPlugin != null) {
                // Re-registering with different plugin
                openGuis.clear();
            }
            Bukkit.getPluginManager().registerEvents(new MagicGuiListener(), plugin);
            registered = true;
            registeredPlugin = plugin;
        }
    }

    /**
     * Register a GUI for a player.
     * 
     * @param player player
     * @param gui    GUI instance
     */
    public static void registerGui(Player player, MagicGui gui) {
        logger.debug("[MagicGuiListener] Registering GUI for player " + player.getName());
        openGuis.put(player, gui);
    }

    /**
     * Unregister a GUI for a player.
     * 
     * @param player player
     */
    public static void unregisterGui(Player player) {
        openGuis.remove(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        logger.debug("[MagicGuiListener] Click event for player " + player.getName() +
                ", openGuis contains: " + openGuis.containsKey(player));

        MagicGui gui = openGuis.get(player);
        if (gui != null) {
            logger.debug("[MagicGuiListener] GUI found, checking inventory match");
            if (event.getInventory().equals(gui.getInventory())) {
                logger.debug("[MagicGuiListener] Inventory matches, calling handleClick");
                gui.handleClick(event);
            } else {
                logger.debug("[MagicGuiListener] Inventory does not match");
            }
        } else {
            logger.debug("[MagicGuiListener] No GUI found for player");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;
        MagicGui gui = openGuis.get(player);
        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            gui.handleClose(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
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
