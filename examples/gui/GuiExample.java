package examples.gui;

import dev.ua.theroer.magicutils.gui.MagicGui;
import dev.ua.theroer.magicutils.gui.MagicGuiListener;
import dev.ua.theroer.magicutils.gui.MagicItem;
import dev.ua.theroer.magicutils.gui.SlotPolicy;
import dev.ua.theroer.magicutils.lang.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal inventory GUI example.
 */
public final class GuiExample {

    private GuiExample() {
    }

    public static void bootstrap(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new MagicGuiListener(), plugin);
    }

    public static void openSettingsMenu(Player player) {
        MagicGui gui = MagicGui.builder()
                .title(Messages.get(player, "magicutils.gui.settings.title"))
                .size(27)
                .slotPolicy(SlotPolicy.FILLER, new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
                .item(13, MagicItem.of(new ItemStack(Material.EMERALD))
                        .name(Messages.get(player, "magicutils.gui.settings.toggle"))
                        .onClick(event -> player.performCommand("settings lang")))
                .build();

        gui.open(player);
    }
}
