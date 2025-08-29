package dev.ua.theroer.magicutils.lang.providers;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;
import java.util.HashMap;
import java.util.Map;

/**
 * Empty map provider
 */
public class EmptyMapProvider implements DefaultValueProvider<Map<String, String>> {
    @Override
    public Map<String, String> provide() {
        return new HashMap<>();
    }
}