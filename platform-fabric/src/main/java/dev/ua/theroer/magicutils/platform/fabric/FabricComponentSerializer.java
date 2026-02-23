package dev.ua.theroer.magicutils.platform.fabric;

import com.google.gson.JsonElement;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.fabric.codec.TextSerializationAdapter;
import dev.ua.theroer.magicutils.platform.fabric.codec.TextSerializationAdapters;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility for converting between Adventure components and Fabric Text.
 */
@SuppressWarnings("doclint:missing")
public final class FabricComponentSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MagicUtils-Fabric-Serializer");
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int DEFAULT_MAX_JSON_LENGTH = 262_144;

    private static volatile int maxJsonLength = DEFAULT_MAX_JSON_LENGTH;
    private static volatile TextSerializationAdapter adapter;

    private FabricComponentSerializer() {
    }

    public static void setMaxJsonLength(int maxJsonLength) {
        FabricComponentSerializer.maxJsonLength = maxJsonLength > 0 ? maxJsonLength : DEFAULT_MAX_JSON_LENGTH;
    }

    public static int getMaxJsonLength() {
        return maxJsonLength;
    }

    public static Text toNative(Component component) {
        return toNative(component, null);
    }

    public static Text nativeParseSmart(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }
        return toNative(MessageParser.parseSmart(input));
    }

    public static Text nativeParseSmart(String template, Map<String, ?> values) {
        String formatted = values != null ? MsgFmt.apply(template, values) : template;
        return nativeParseSmart(formatted);
    }

    public static List<Text> nativeParseSmartList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .map(FabricComponentSerializer::nativeParseSmart)
                .toList();
    }

    public static Text toNative(Component component, Consumer<String> onError) {
        if (component == null) {
            return Text.empty();
        }

        try {
            JsonElement tree = GSON.serializeToTree(component);
            Text decoded = decodeText(tree, onError);
            if (decoded != null) {
                return decoded;
            }
        } catch (Exception e) {
            if (onError != null) {
                onError.accept(e.toString());
            }
        }

        return Text.literal(PLAIN.serialize(component));
    }

    public static Component toAdventure(Text text) {
        return toAdventure(text, null);
    }

    public static Component toAdventure(Text text, RegistryWrapper.WrapperLookup registries) {
        if (text == null) {
            return Component.empty();
        }

        try {
            JsonElement tree = encodeText(text, registries);
            if (tree != null) {
                return GSON.deserializeFromTree(tree);
            }
        } catch (Exception ignored) {
            // plain fallback below
        }
        return Component.text(Objects.toString(text.getString(), ""));
    }

    public static String toPlain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    public static Supplier<Text> fbLazyMemoized(Component component) {
        AtomicReference<Text> ref = new AtomicReference<>();
        return () -> {
            Text cached = ref.get();
            if (cached != null) {
                return cached;
            }
            Text created = toNative(component);
            ref.compareAndSet(null, created);
            return ref.get();
        };
    }

    private static Text decodeText(JsonElement tree, Consumer<String> onError) {
        return adapter().decode(tree, onError);
    }

    private static JsonElement encodeText(Text text, RegistryWrapper.WrapperLookup registries) {
        return adapter().encode(text, registries);
    }

    private static TextSerializationAdapter adapter() {
        TextSerializationAdapter current = adapter;
        if (current != null) {
            return current;
        }
        synchronized (FabricComponentSerializer.class) {
            if (adapter == null) {
                adapter = TextSerializationAdapters.detect(LOGGER);
            }
            return adapter;
        }
    }
}
