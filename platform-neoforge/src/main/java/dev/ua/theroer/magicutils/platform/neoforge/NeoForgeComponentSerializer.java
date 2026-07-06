package dev.ua.theroer.magicutils.platform.neoforge;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.network.chat.ComponentSerialization;

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
            net.minecraft.network.chat.Component decoded = decode(tree);
            if (decoded != null) {
                return decoded;
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
}
