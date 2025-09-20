package examples.config;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal example that maps a YAML file to a POJO and reacts to runtime reloads.
 */
public final class ConfigExample {

    private ConfigExample() {
    }

    @ConfigFile("examples/example.yml")
    @Getter
    @Comment("Example configuration created by ConfigManager")
    public static class ExampleConfig {

        @ConfigValue("enabled")
        @Comment("Whether the feature is active")
        private boolean enabled = true;

        @ConfigSection("messages")
        private Messages messages = new Messages();

        @Getter
        @Comment("Strings displayed to players")
        public static class Messages {
            @ConfigValue("greeting")
            private String greeting = "&aHello, world!";

            @ConfigValue("farewell")
            private String farewell = "&cGoodbye!";
        }
    }

    /**
     * Demonstrates registration, change listening, and manual saves.
     */
    public static void bootstrap(JavaPlugin plugin) {
        ConfigManager manager = new ConfigManager(plugin);
        ExampleConfig config = manager.register(ExampleConfig.class);

        manager.onChange(ExampleConfig.class, (updated, sections) ->
                Logger.info("<gray>Reloaded sections:</gray> <green>{sections}</green>",
                        TagResolvers.sections(sections)));

        if (config.isEnabled()) {
            Logger.info("<green>Feature enabled!</green>");
        }
    }

    private static final class TagResolvers {
        private TagResolvers() {
        }

        static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver sections(java.util.Set<String> sections) {
            return net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.placeholder(
                    "sections",
                    String.join(", ", sections));
        }
    }
}
