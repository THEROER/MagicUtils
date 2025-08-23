package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

// Default value providers
public class DefaultChatGradientProvider implements DefaultValueProvider<List<String>> {
    @Override
    public List<String> provide() {
        return Arrays.asList("#7c3aed", "#ec4899");
    }
}