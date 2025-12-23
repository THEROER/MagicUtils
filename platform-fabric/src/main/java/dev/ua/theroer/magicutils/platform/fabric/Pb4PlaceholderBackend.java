package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Audience;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class Pb4PlaceholderBackend implements FabricPlaceholderBackend {
    private final Set<MagicPlaceholders.PlaceholderKey> registered = ConcurrentHashMap.newKeySet();

    @Override
    public void registerAll() {
        for (MagicPlaceholders.PlaceholderKey key : MagicPlaceholders.entries().keySet()) {
            register(key);
        }
    }

    @Override
    public void register(MagicPlaceholders.PlaceholderKey key) {
        if (key == null || !registered.add(key)) {
            return;
        }
        Identifier identifier = Identifier.of(key.namespace(), key.key());
        Placeholders.register(identifier, (ctx, arg) -> resolve(key, ctx, arg));
    }

    @Override
    public void unregister(MagicPlaceholders.PlaceholderKey key) {
        if (key == null) {
            return;
        }
        registered.remove(key);
        Placeholders.remove(Identifier.of(key.namespace(), key.key()));
    }

    @Override
    public void updateNamespace(String namespace) {
    }

    private PlaceholderResult resolve(MagicPlaceholders.PlaceholderKey key, PlaceholderContext ctx, String arg) {
        Audience audience = ctx != null && ctx.player() != null ? new FabricAudience(ctx.player()) : null;
        String value = MagicPlaceholders.resolve(key.namespace(), key.key(), audience, arg);
        if (value == null || value.isEmpty()) {
            return PlaceholderResult.value(Text.empty());
        }
        return PlaceholderResult.value(FabricComponentSerializer.toNative(MessageParser.parseSmart(value)));
    }
}
