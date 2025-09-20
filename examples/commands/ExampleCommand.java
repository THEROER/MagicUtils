package examples.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.Permission;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandContext;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.parsers.LanguageKeyTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simple command that shows how to wire sub-commands via annotations.
 */
@CommandInfo(name = "example", description = "MagicUtils command example", permission = true)
public class ExampleCommand extends MagicCommand {

    public ExampleCommand(LanguageManager languageManager, JavaPlugin plugin) {
        LanguageKeyTypeParser.setPlugin(plugin);
    }

    @SubCommand(name = "ping", description = "Send yourself a greeting")
    public CommandResult ping(CommandContext context, Player sender,
            @OptionalArgument String targetKey) {
        String key = targetKey != null ? targetKey : InternalMessages.CMD_EXECUTED.getKey();
        Messages.send(sender, InternalMessages.SYS_COMMAND_USAGE.getKey(), "usage", "/example ping");
        return CommandResult.success(key);
    }

    @SubCommand(name = "setlang", description = "Change your language")
    @Permission("magicutils.example.lang")
    public CommandResult setLanguage(CommandContext context, Player sender,
            @Suggest("@languages") String languageCode) {
        if (!LanguageManagerProvider.get().setPlayerLanguage(sender, languageCode)) {
            return CommandResult.failure(InternalMessages.SETTINGS_LANG_NOT_FOUND.get("language", languageCode));
        }
        return CommandResult.success(InternalMessages.SETTINGS_KEY_SET.get("language", languageCode, "key", "lang"));
    }

    /**
     * Register the command and make the language manager accessible to the example.
     */
    public static void register(LanguageManager languageManager, JavaPlugin plugin) {
        LanguageManagerProvider.init(languageManager);
        CommandRegistry.register(new ExampleCommand(languageManager, plugin));
    }

    private static final class LanguageManagerProvider {
        private static LanguageManager languageManager;

        private LanguageManagerProvider() {
        }

        static void init(LanguageManager manager) {
            languageManager = manager;
        }

        static LanguageManager get() {
            if (languageManager == null) {
                throw new IllegalStateException("LanguageManagerProvider not initialised");
            }
            return languageManager;
        }
    }
}
