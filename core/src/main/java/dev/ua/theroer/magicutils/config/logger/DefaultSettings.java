package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Default settings for logger behavior.
 * Controls default message routing and formatting options.
 */
@Data
public class DefaultSettings {

    /**
     * Default constructor for DefaultSettings.
     */
    public DefaultSettings() {
    }

    @ConfigValue("target")
    @Comment("Default target for messages (CHAT, CONSOLE, BOTH)")
    private String target = "BOTH";

    @ConfigValue("text-max-length")
    @Comment("Max JSON length for Component/Text conversion (Fabric only)")
    private int textMaxLength = 262_144;

    @ConfigValue("placeholder-engine-order")
    @Comment("Order for external placeholder engines (MINI_PLACEHOLDERS, PB4, PAPI)")
    private List<String> placeholderEngineOrder = new ArrayList<>(List.of("MINI_PLACEHOLDERS", "PB4"));

    @ConfigValue("miniplaceholders-mode")
    @Comment("MiniPlaceholders mode (COMPONENT, TAG)")
    private MiniPlaceholdersMode miniPlaceholdersMode = MiniPlaceholdersMode.COMPONENT;

    @ConfigValue("pb4-mode")
    @Comment("PB4 placeholder mode (COMPONENT, RAW)")
    private Pb4Mode pb4Mode = Pb4Mode.COMPONENT;

    public enum MiniPlaceholdersMode {
        COMPONENT,
        TAG
    }

    public enum Pb4Mode {
        COMPONENT,
        RAW
    }
}
