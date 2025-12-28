package dev.ua.theroer.magicutils.platform.fabric;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.utils.MsgFmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility for converting between Adventure components and Fabric Text.
 */
@SuppressWarnings("doclint:missing")
public final class FabricComponentSerializer {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int DEFAULT_MAX_JSON_LENGTH = 262_144;

    private static volatile int maxJsonLength = DEFAULT_MAX_JSON_LENGTH;

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

        String plain = PLAIN.serialize(component);
        try {
            JsonElement tree = GSON.serializeToTree(component);
            DataResult<Text> decoded = TextCodecs.withJsonLengthLimit(maxJsonLength).parse(JsonOps.INSTANCE, tree);
            if (onError != null) {
                decoded.error().ifPresent(err -> onError.accept(err.message()));
            }
            return decoded.result().orElseGet(() -> Text.literal(plain));
        } catch (Exception e) {
            if (onError != null) {
                onError.accept(e.toString());
            }
            return Text.literal(plain);
        }
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
            if (tree == null) {
                return Component.text(Objects.toString(text.getString(), ""));
            }
            return GSON.deserializeFromTree(tree);
        } catch (Exception e) {
            return Component.text(Objects.toString(text.getString(), ""));
        }
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

    private static JsonElement encodeText(Text text, RegistryWrapper.WrapperLookup registries) {
        var ops = registries != null ? RegistryOps.of(JsonOps.INSTANCE, registries) : JsonOps.INSTANCE;
        DataResult<JsonElement> encoded = TextCodecs.withJsonLengthLimit(maxJsonLength).encodeStart(ops, text);
        return encoded.result().orElse(null);
    }
}
