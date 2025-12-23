package dev.ua.theroer.magicutils.platform.bukkit;

import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;

interface BukkitPlaceholderBackend {
    void registerAll();

    void ensureNamespace(String namespace);

    void unregisterNamespace(String namespace);

    void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key);
}
