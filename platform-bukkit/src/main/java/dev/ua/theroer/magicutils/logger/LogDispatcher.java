package dev.ua.theroer.magicutils.logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles recipient selection and thread-safe delivery for logger messages.
 */
public final class LogDispatcher {
    private LogDispatcher() {
    }

    /**
     * Resolves concrete recipients based on provided player, collection, and broadcast flag.
     *
     * @param player    direct player recipient
     * @param players   collection of players to include
     * @param broadcast whether message should be sent to all players
     * @param target    target channel (chat or console)
     * @return collection of senders to deliver chat messages to
     */
    public static Collection<CommandSender> determineRecipients(
            Player player,
            Collection<? extends Player> players,
            boolean broadcast,
            LogTarget target) {

        if (target == LogTarget.CONSOLE) {
            return Collections.emptyList();
        }

        if (broadcast) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }

        List<CommandSender> recipients = new ArrayList<>();
        if (player != null) {
            recipients.add(player);
        }
        if (players != null && !players.isEmpty()) {
            recipients.addAll(players);
        }
        return recipients;
    }

    /**
     * Sends the component to console and/or chat in a thread-safe way.
     *
     * @param plugin     owning plugin used for scheduling sync tasks
     * @param component  message component to send
     * @param recipients resolved chat recipients (ignored for console-only)
     * @param target     LogTarget describing where to deliver
     */
    public static void deliver(JavaPlugin plugin, Component component, Collection<CommandSender> recipients, LogTarget target) {
        if (target == LogTarget.CONSOLE || target == LogTarget.BOTH) {
            Bukkit.getConsoleSender().sendMessage(component);
        }

        if (target == LogTarget.CHAT || target == LogTarget.BOTH) {
            if (recipients.isEmpty()) {
                return;
            }
            if (Bukkit.isPrimaryThread()) {
                recipients.forEach(r -> r.sendMessage(component));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> recipients.forEach(r -> r.sendMessage(component)));
            }
        }
    }
}
