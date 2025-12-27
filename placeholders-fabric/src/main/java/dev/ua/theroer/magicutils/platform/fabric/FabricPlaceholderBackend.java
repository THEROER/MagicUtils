package dev.ua.theroer.magicutils.platform.fabric;

import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;

interface FabricPlaceholderBackend {
    void registerAll();

    void register(MagicPlaceholders.PlaceholderKey key);

    void unregister(MagicPlaceholders.PlaceholderKey key);

    void updateNamespace(String namespace);
}
