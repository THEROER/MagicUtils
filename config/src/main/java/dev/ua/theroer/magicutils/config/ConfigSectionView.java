package dev.ua.theroer.magicutils.config;

import java.util.Map;

/**
 * Lightweight view over a configuration section (backed by a map).
 */
class ConfigSectionView {
    private final Map<String, Object> backing;

    ConfigSectionView(Map<String, Object> backing) {
        this.backing = backing;
    }

    Map<String, Object> unwrap() {
        return backing;
    }
}
