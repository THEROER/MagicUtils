package dev.ua.theroer.magicutils.config.annotations;

/**
 * Dummy provider class used as default value for DefaultValue annotation.
 * This class should not be instantiated and is only used to indicate
 * that no provider is specified.
 */
public final class NoDefaultValueProvider implements DefaultValueProvider<Object> {
    /**
     * Private constructor to prevent instantiation.
     */
    private NoDefaultValueProvider() {
        throw new UnsupportedOperationException("NoDefaultValueProvider should not be instantiated");
    }

    /**
     * This method should never be called.
     * 
     * @return never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public Object provide() {
        throw new UnsupportedOperationException("NoDefaultValueProvider should not be instantiated");
    }
}