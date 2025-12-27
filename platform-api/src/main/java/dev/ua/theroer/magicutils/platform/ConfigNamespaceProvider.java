package dev.ua.theroer.magicutils.platform;

/**
 * Optional platform hook for namespaced config storage.
 */
public interface ConfigNamespaceProvider {
    /**
     * Resolve config namespace for the current platform.
     *
     * @param pluginName plugin/mod name, if available
     * @return namespace or null to disable namespacing
     */
    String resolveConfigNamespace(String pluginName);
}
