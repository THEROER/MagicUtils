package dev.ua.theroer.magicutils.platform.fabric.codec;

import com.google.gson.JsonElement;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

/**
 * Adapter for Minecraft Text serialization and deserialization.
 * Supports different internal mechanisms (e.g., legacy Text.Serializer and modern TextCodecs).
 */
public interface TextSerializationAdapter {
    /**
     * Decodes a JSON element into a Minecraft Text object.
     *
     * @param tree the JSON element to decode
     * @param onError a consumer to handle errors during decoding
     * @return the decoded Text object, or null if input is null
     */
    Component decode(JsonElement tree, Consumer<String> onError);

    /**
     * Encodes a Minecraft Text object into a JSON element.
     *
     * @param text the Text object to encode
     * @param registries the registry wrapper lookup for context-aware encoding (can be null)
     * @return the encoded JSON element, or null if input is null
     */
    JsonElement encode(Component text, HolderLookup.Provider registries);

    /**
     * Gets the name of the adapter or the underlying mechanism.
     *
     * @return the adapter name
     */
    String name();
}
