package examples.utils;

import dev.ua.theroer.magicutils.utils.ScheduleUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Illustrates the scheduling helpers provided by MagicUtils.
 */
public final class ScheduleExample {

    private ScheduleExample() {
    }

    public static void runCountdown(Player player, Plugin plugin) {
        ScheduleUtils.countdown(5,
                seconds -> player.sendActionBar(net.kyori.adventure.text.Component.text("Starting in " + seconds)),
                () -> player.sendActionBar(net.kyori.adventure.text.Component.text("Go!")));

        ScheduleUtils.repeat(3, 20L,
                () -> player.sendMessage("tick"),
                () -> player.sendMessage("done"),
                plugin);
    }
}
