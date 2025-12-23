package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.config.logger.DefaultSettings;
import dev.ua.theroer.magicutils.logger.ExternalPlaceholderEngine;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.ColorUtils;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MiniPlaceholdersBackend implements FabricPlaceholderBackend {
    private static final int MAX_LOG_VALUE = 256;
    private final Map<String, Expansion> expansions = new ConcurrentHashMap<>();
    private final LoggerCore logger;

    MiniPlaceholdersBackend() {
        this(null);
    }

    MiniPlaceholdersBackend(LoggerCore logger) {
        this.logger = logger;
    }

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
        Audience audience = resolveAudience(adventureAudience);
        String value = MagicPlaceholders.resolve(key.namespace(), key.key(), audience, argument);
        if (value == null || value.isEmpty()) {
            logDebug(key, argument, audience, value, value, Component.empty());
            return Tag.inserting(Component.empty());
        }
        String resolved = applyNestedPlaceholders(value, audience);
        DefaultSettings.MiniPlaceholdersMode mode = DefaultSettings.MiniPlaceholdersMode.COMPONENT;
        if (logger != null && logger.getConfig() != null) {
            DefaultSettings defaults = logger.getConfig().getDefaults();
            if (defaults != null && defaults.getMiniPlaceholdersMode() != null) {
                mode = defaults.getMiniPlaceholdersMode();
            }
        }
        if (mode == DefaultSettings.MiniPlaceholdersMode.TAG) {
            String raw = toMiniMessage(resolved);
            logDebug(key, argument, audience, value, resolved, MessageParser.parseSmart(resolved));
            return Tag.preProcessParsed(raw);
        }
        Component parsed = MessageParser.parseSmart(resolved);
        logDebug(key, argument, audience, value, resolved, parsed);
        return Tag.inserting(parsed);
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

    private void logDebug(MagicPlaceholders.PlaceholderKey key,
                          String argument,
                          Audience audience,
                          String raw,
                          String resolved,
                          Component parsed) {
        if (!isDebugEnabled()) {
            return;
        }
        String keyName = key != null ? key.namespace() + ":" + key.key() : "unknown";
        String uuid = audience != null && audience.id() != null ? audience.id().toString() : "null";
        String mini = parsed != null ? logger.getMiniMessage().serialize(parsed) : "";
        String plain = parsed != null ? FabricComponentSerializer.toPlain(parsed) : "";
        logger.getPlatform().logger().info(
                "[MagicUtils][Placeholders][Mini] key=" + keyName
                        + " arg=" + sanitize(argument)
                        + " uuid=" + uuid
                        + " raw=" + sanitize(raw)
                        + " resolved=" + sanitize(resolved)
                        + " mini=" + sanitize(mini)
                        + " plain=" + sanitize(plain)
        );
    }

    private boolean isDebugEnabled() {
        if (logger == null || logger.getConfig() == null) {
            return false;
        }
        return logger.getConfig().isDebugPlaceholders() || logger.getConfig().isDebugCommands();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        String normalized = value.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() > MAX_LOG_VALUE) {
            return normalized.substring(0, MAX_LOG_VALUE) + "...(" + normalized.length() + ")";
        }
        return normalized;
    }

    private String toMiniMessage(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean hasLegacy = value.indexOf('&') >= 0 || value.indexOf('§') >= 0;
        return hasLegacy ? ColorUtils.legacyToMiniMessage(value) : value;
    }

    private Audience resolveAudience(net.kyori.adventure.audience.Audience adventureAudience) {
        UUID uuid = resolveUuid(adventureAudience);
        if (uuid == null) {
            return null;
        }
        Audience online = lookupOnline(uuid);
        return online != null ? online : MagicPlaceholders.audienceFromUuid(uuid);
    }

    private Audience lookupOnline(UUID uuid) {
        if (logger == null || uuid == null) {
            return null;
        }
        Collection<Audience> players = logger.getPlatform().onlinePlayers();
        if (players == null || players.isEmpty()) {
            return null;
        }
        for (Audience audience : players) {
            if (audience != null && uuid.equals(audience.id())) {
                return audience;
            }
        }
        return null;
    }

    private String applyNestedPlaceholders(String value, Audience audience) {
        if (value == null || value.isEmpty() || logger == null) {
            return value;
        }
        ExternalPlaceholderEngine engine = logger.getExternalPlaceholderEngine();
        if (engine instanceof FabricExternalPlaceholderEngine fabricEngine) {
            return fabricEngine.applyPb4(audience, value, false);
        }
        return value;
    }

}
