package dev.ua.theroer.magicutils.platform.fabric.codec;

import com.google.gson.JsonElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public interface TextSerializationAdapter {
    Text decode(JsonElement tree, Consumer<String> onError);

    JsonElement encode(Text text, RegistryWrapper.WrapperLookup registries);

    String name();
}
