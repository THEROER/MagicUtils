package dev.ua.theroer.magicutils.logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes a leading textual prefix from a component while preserving formatting.
 */
public final class ComponentPrefixStripper {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ComponentPrefixStripper() {
    }

    /**
     * Strips a plain-text prefix from the component.
     *
     * @param component component to trim
     * @param prefixText prefix text to remove
     * @return component without the prefix
     */
    public static Component stripPrefix(Component component, String prefixText) {
        if (component == null || prefixText == null || prefixText.isBlank()) {
            return component;
        }
        int toRemove = prefixText.length();
        if (toRemove <= 0) {
            return component;
        }
        return trimLeading(component, toRemove);
    }

    private static Component trimLeading(Component component, int toRemove) {
        if (component == null || toRemove <= 0) {
            return component;
        }

        if (component instanceof TextComponent text) {
            int remaining = toRemove;
            String content = text.content();
            if (!content.isEmpty()) {
                int removeFromContent = Math.min(remaining, content.length());
                content = content.substring(removeFromContent);
                remaining -= removeFromContent;
            }

            List<Component> children = text.children();
            if (children.isEmpty()) {
                return Component.text(content, text.style());
            }

            List<Component> newChildren = trimChildren(children, remaining);
            TextComponent.Builder builder = Component.text().content(content).style(text.style());
            builder.append(newChildren);
            return builder.build();
        }

        List<Component> children = component.children();
        if (children.isEmpty()) {
            return component;
        }
        List<Component> newChildren = trimChildren(children, toRemove);
        TextComponent.Builder builder = Component.text().style(component.style());
        builder.append(newChildren);
        return builder.build();
    }

    private static List<Component> trimChildren(List<Component> children, int toRemove) {
        if (children.isEmpty()) {
            return children;
        }
        int remaining = toRemove;
        List<Component> newChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            if (remaining <= 0) {
                newChildren.add(child);
                continue;
            }
            int childLength = plainLength(child);
            if (childLength <= 0) {
                newChildren.add(child);
                continue;
            }
            if (remaining >= childLength) {
                remaining -= childLength;
                continue;
            }
            Component trimmedChild = trimLeading(child, remaining);
            remaining = 0;
            newChildren.add(trimmedChild);
        }
        return newChildren;
    }

    private static int plainLength(Component component) {
        if (component == null) {
            return 0;
        }
        String plain = PLAIN.serialize(component);
        return plain != null ? plain.length() : 0;
    }
}
