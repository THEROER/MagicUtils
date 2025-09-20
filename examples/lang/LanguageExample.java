package examples.lang;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Demonstrates per-player language selection and message sending.
 */
public final class LanguageExample {

    private LanguageExample() {
    }

    public static LanguageManager bootstrap(JavaPlugin plugin, dev.ua.theroer.magicutils.config.ConfigManager configManager) {
        LanguageManager languageManager = new LanguageManager(plugin, configManager);
        languageManager.init("en");
        languageManager.setFallbackLanguage("en");

        // Register internal messages so Logger & commands can use localisation.
        languageManager.addMagicUtilsMessages();

        // Make Messages facade aware of the manager.
        Messages.setLanguageManager(languageManager);
        Logger.setLanguageManager(languageManager);
        return languageManager;
    }

    public static void chooseLanguage(Player player, LanguageManager languageManager, String code) {
        boolean ok = languageManager.setPlayerLanguage(player, code);
        if (ok) {
            Messages.send(player, "magicutils.system.loaded_language", Map.of("language", code));
        } else {
            Messages.send(player, "magicutils.system.failed_load_language", Map.of("language", code));
        }
    }
}
