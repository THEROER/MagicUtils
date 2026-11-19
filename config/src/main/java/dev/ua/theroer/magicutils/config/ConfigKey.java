package dev.ua.theroer.magicutils.config;

import java.util.Objects;

/**
 * Identity of a registered config: its class plus resolved relative file path.
 */
final class ConfigKey {
    final Class<?> configClass;
    final String filePath;

    ConfigKey(Class<?> configClass, String filePath) {
        this.configClass = configClass;
        this.filePath = filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConfigKey configKey = (ConfigKey) o;
        return Objects.equals(configClass, configKey.configClass)
                && Objects.equals(filePath, configKey.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configClass, filePath);
    }
}
