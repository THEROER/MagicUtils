package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.platform.Audience;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PapiPlaceholderBackend implements BukkitPlaceholderBackend {
    private final Map<String, MagicUtilsExpansion> expansions = new ConcurrentHashMap<>();

    PapiPlaceholderBackend(JavaPlugin plugin) {
    }

    @Override
    public void registerAll() {
        for (String namespace : MagicPlaceholders.namespaces()) {
            ensureNamespace(namespace);
        }
    }

    @Override
    public void ensureNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return;
        }
        String normalized = namespace.toLowerCase(Locale.ROOT);
        if (expansions.containsKey(normalized)) {
            return;
        }
        MagicUtilsExpansion expansion = new MagicUtilsExpansion(normalized);
        if (expansion.register()) {
            expansions.put(normalized, expansion);
        }
    }

    @Override
    public void unregisterNamespace(String namespace) {
        if (namespace == null) {
            return;
        }
        MagicUtilsExpansion expansion = expansions.remove(namespace.toLowerCase(Locale.ROOT));
        if (expansion != null) {
            expansion.unregister();
        }
    }

    @Override
    public void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key) {
        ensureNamespace(key.namespace());
    }

    private final class MagicUtilsExpansion extends PlaceholderExpansion {
        private final String namespace;

        private MagicUtilsExpansion(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public String getIdentifier() {
            return namespace;
        }

        @Override
        public String getAuthor() {
            return MagicPlaceholders.getNamespaceMeta(namespace).author();
        }

        @Override
        public String getVersion() {
            return MagicPlaceholders.getNamespaceMeta(namespace).version();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            return resolve(player, params);
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            return resolve(player, params);
        }

        private String resolve(OfflinePlayer player, String params) {
            if (params == null || params.isBlank()) {
                return "";
            }
            String key = params;
            String argument = null;
            int colon = params.indexOf(':');
            if (colon > -1) {
                key = params.substring(0, colon);
                argument = params.substring(colon + 1);
            }
            Audience audience = null;
            if (player != null) {
                UUID uuid = player.getUniqueId();
                if (player instanceof Player online) {
                    audience = new BukkitAudienceWrapper(online);
                } else if (uuid != null) {
                    audience = MagicPlaceholders.audienceFromUuid(uuid);
                }
            }
            return MagicPlaceholders.resolve(namespace, key, audience, argument);
        }
    }
}
