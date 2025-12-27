package dev.ua.theroer.magicutils.platform;

/**
 * Optional platform hook for default config formats.
 */
public interface ConfigFormatProvider {
    /**
     * Resolve default config extension for this platform.
     *
     * @return extension (e.g. "jsonc")
     */
    String defaultConfigExtension();
}
