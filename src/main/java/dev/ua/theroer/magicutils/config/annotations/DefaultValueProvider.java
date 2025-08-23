package dev.ua.theroer.magicutils.config.annotations;

/**
 * Interface for providing default values for complex types.
 * @param <T> the type of value to provide
 */
public interface DefaultValueProvider<T> {
    /**
     * Provides the default value.
     * @return the default value
     */
    T provide();
}