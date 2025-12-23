package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Audience;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MiniPlaceholdersBackend implements FabricPlaceholderBackend {
    private final Map<String, Expansion> expansions = new ConcurrentHashMap<>();

    @Override
    public void registerAll() {
        for (String namespace : MagicPlaceholders.namespaces()) {
            rebuildNamespace(namespace);
        }
    }

    @Override
    public void register(MagicPlaceholders.PlaceholderKey key) {
        if (key != null) {
            rebuildNamespace(key.namespace());
        }
    }

    @Override
    public void unregister(MagicPlaceholders.PlaceholderKey key) {
        if (key != null) {
            rebuildNamespace(key.namespace());
        }
    }

    @Override
    public void updateNamespace(String namespace) {
        rebuildNamespace(namespace);
    }

    private void rebuildNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return;
        }
        List<MagicPlaceholders.PlaceholderKey> keys = MagicPlaceholders.keysForNamespace(namespace);
        Expansion existing = expansions.remove(namespace);
        if (existing != null && existing.registered()) {
            existing.unregister();
        }
        if (keys.isEmpty()) {
            return;
        }

        MagicPlaceholders.NamespaceMeta meta = MagicPlaceholders.getNamespaceMeta(namespace);
        Expansion.Builder builder = Expansion.builder(namespace)
                .author(meta.author())
                .version(meta.version());

        for (MagicPlaceholders.PlaceholderKey key : keys) {
            builder.audiencePlaceholder(key.key(), (audience, queue, ctx) -> resolveTag(key, audience, queue));
        }

        Expansion expansion = builder.build();
        expansion.register();
        expansions.put(namespace, expansion);
    }

    private Tag resolveTag(MagicPlaceholders.PlaceholderKey key,
                           net.kyori.adventure.audience.Audience adventureAudience,
                           ArgumentQueue queue) {
        String argument = drainArguments(queue);
        Audience audience = MagicPlaceholders.audienceFromUuid(resolveUuid(adventureAudience));
        String value = MagicPlaceholders.resolve(key.namespace(), key.key(), audience, argument);
        if (value == null || value.isEmpty()) {
            return Tag.inserting(Component.empty());
        }
        return Tag.inserting(MessageParser.parseSmart(value));
    }

    private UUID resolveUuid(net.kyori.adventure.audience.Audience audience) {
        if (audience == null) {
            return null;
        }
        return audience.get(Identity.UUID).orElse(null);
    }

    private String drainArguments(ArgumentQueue queue) {
        if (queue == null || !queue.hasNext()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        while (queue.hasNext()) {
            String part = queue.pop().value();
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
