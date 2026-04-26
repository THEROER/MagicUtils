package dev.ua.theroer.magicutils.platform.bungee;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Converts Adventure components to Bungee chat components.
 */
public final class BungeeComponentSerializer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private BungeeComponentSerializer() {
    }

    /**
     * Converts an Adventure component to Bungee components.
     *
     * @param component Adventure component
     * @return Bungee component array
     */
    public static BaseComponent[] toBaseComponents(Component component) {
        if (component == null) {
            return new BaseComponent[0];
        }
        return TextComponent.fromLegacyText(LEGACY.serialize(component));
    }

    /**
     * Converts an Adventure component to a single Bungee component tree.
     *
     * @param component Adventure component
     * @return Bungee component
     */
    public static BaseComponent toBaseComponent(Component component) {
        BaseComponent[] components = toBaseComponents(component);
        if (components.length == 0) {
            return new TextComponent("");
        }
        if (components.length == 1) {
            return components[0];
        }
        return new TextComponent(components);
    }

    /**
     * Renders an Adventure component as legacy text.
     *
     * @param component Adventure component
     * @return legacy text
     */
    public static String toLegacyText(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY.serialize(component);
    }

    /**
     * Renders Bungee components as plain text.
     *
     * @param component Bungee component
     * @return plain text
     */
    public static String toPlainText(BaseComponent component) {
        if (component == null) {
            return "";
        }
        return BaseComponent.toPlainText(component);
    }

    /**
     * Renders Bungee components as plain text.
     *
     * @param components Bungee components
     * @return plain text
     */
    public static String toPlainText(BaseComponent... components) {
        if (components == null || components.length == 0) {
            return "";
        }
        return BaseComponent.toPlainText(components);
    }

    /**
     * Renders legacy-formatted text as plain text.
     *
     * @param legacy legacy formatted text
     * @return plain text
     */
    public static String legacyToPlainText(String legacy) {
        if (legacy == null || legacy.isBlank()) {
            return "";
        }
        return BaseComponent.toPlainText(TextComponent.fromLegacyText(legacy));
    }
}
