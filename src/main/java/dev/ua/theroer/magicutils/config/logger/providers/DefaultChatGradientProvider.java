package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for chat gradient colors.
 * Provides a purple-to-pink gradient color scheme for chat messages.
 */
public class DefaultChatGradientProvider implements DefaultValueProvider<List<String>> {

    /**
     * Default constructor for DefaultChatGradientProvider.
     */
    public DefaultChatGradientProvider() {
    }

    @Override
    public List<String> provide() {
        return Arrays.asList("#7c3aed", "#ec4899");
    }
}