package dev.ua.theroer.magicutils.platform.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Map;

/**
 * Utility for converting Adventure components to NeoForge chat components.
 */
public final class NeoForgeComponentSerializer {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private NeoForgeComponentSerializer() {
    }

    /**
     * Converts an Adventure component to a native Minecraft component.
     *
     * @param component adventure component (nullable)
     * @return native component
     */
    public static net.minecraft.network.chat.Component toNative(Component component) {
        if (component == null) {
            return net.minecraft.network.chat.Component.empty();
        }
        try {
            JsonElement tree = GSON.serializeToTree(component);
            // Adventure 4.24+ emits the 1.21.5+ event shape (`click_event`/
            // `hover_event` carrying `command`/`url`), which a pre-1.21.5 codec does
            // not merely reject — it parses the component fine and silently drops the
            // unknown fields, so colours survive but clicks and hovers vanish. Try
            // the legacy shape (`clickEvent`/`hoverEvent` with `value`) first and
            // keep whichever result actually preserves the events.
            JsonElement legacy = toLegacyEvents(tree);
            if (legacy != null) {
                net.minecraft.network.chat.Component legacyDecoded = decode(legacy);
                if (legacyDecoded != null && hasEvents(legacyDecoded)) {
                    return legacyDecoded;
                }
                net.minecraft.network.chat.Component modern = decode(tree);
                if (modern != null) {
                    return modern;
                }
                if (legacyDecoded != null) {
                    return legacyDecoded;
                }
            } else {
                net.minecraft.network.chat.Component decoded = decode(tree);
                if (decoded != null) {
                    return decoded;
                }
            }
        } catch (Exception ignored) {
        }
        String plain = PLAIN.serialize(component);
        if (plain == null) {
            plain = "";
        }
        return net.minecraft.network.chat.Component.literal(plain);
    }

    /**
     * Decodes an Adventure-produced JSON tree into a native component via the
     * vanilla {@link ComponentSerialization#CODEC}. Since MC 1.20.5 the old
     * reflective {@code Component$Serializer.fromJson(...)} no longer exists —
     * decoding goes through the component codec, so colours/formatting/hover
     * events survive instead of being flattened to plain text.
     */
    private static net.minecraft.network.chat.Component decode(JsonElement tree) {
        return ComponentSerialization.CODEC
                .parse(JsonOps.INSTANCE, tree)
                .result()
                .orElse(null);
    }

    /**
     * Reports whether the decoded component carries a click or hover event
     * anywhere in its tree — the signal that the codec understood the event
     * shape we handed it rather than dropping the unknown fields.
     */
    private static boolean hasEvents(net.minecraft.network.chat.Component component) {
        if (component == null) {
            return false;
        }
        net.minecraft.network.chat.Style style = component.getStyle();
        if (style != null && (style.getClickEvent() != null || style.getHoverEvent() != null)) {
            return true;
        }
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            if (hasEvents(sibling)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rewrites the modern snake_case event fields into the pre-1.21.5 shape,
     * returning {@code null} when the tree carries no such fields (so the caller
     * can skip the retry). The input tree is left untouched.
     */
    private static JsonElement toLegacyEvents(JsonElement tree) {
        Rewriter rewriter = new Rewriter();
        JsonElement rewritten = rewriter.rewrite(tree);
        return rewriter.changed ? rewritten : null;
    }

    private static final class Rewriter {
        private boolean changed;

        private JsonElement rewrite(JsonElement element) {
            if (element instanceof JsonArray array) {
                JsonArray copy = new JsonArray();
                for (JsonElement child : array) {
                    copy.add(rewrite(child));
                }
                return copy;
            }
            if (!(element instanceof JsonObject object)) {
                return element;
            }

            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String name = entry.getKey();
                JsonElement value = entry.getValue();
                switch (name) {
                    case "click_event" -> {
                        changed = true;
                        copy.add("clickEvent", rewriteClick(value));
                    }
                    case "hover_event" -> {
                        changed = true;
                        copy.add("hoverEvent", rewriteHover(value));
                    }
                    default -> copy.add(name, rewrite(value));
                }
            }
            return copy;
        }

        /**
         * {@code {"action":"run_command","command":"/x"}} becomes
         * {@code {"action":"run_command","value":"/x"}}. The payload key depends
         * on the action: {@code command} for run/suggest, {@code url} for
         * open_url, {@code path} for open_file, {@code page} for change_page.
         */
        private JsonElement rewriteClick(JsonElement value) {
            if (!(value instanceof JsonObject event)) {
                return rewrite(value);
            }
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : event.entrySet()) {
                String name = entry.getKey();
                switch (name) {
                    case "command", "url", "path", "page" -> copy.add("value", entry.getValue());
                    default -> copy.add(name, rewrite(entry.getValue()));
                }
            }
            return copy;
        }

        /**
         * Hover events keep their {@code value}/{@code contents} payload; only
         * nested components inside it need rewriting.
         */
        private JsonElement rewriteHover(JsonElement value) {
            return rewrite(value);
        }
    }
}
