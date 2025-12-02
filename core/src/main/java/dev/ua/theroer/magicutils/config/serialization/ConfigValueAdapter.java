package dev.ua.theroer.magicutils.config.serialization;

/**
 * Adapter for custom types used in configs.
 * Provides explicit serialize/deserialize hooks to and from raw YAML values.
 *
 * @param <T> target Java type
 */
public interface ConfigValueAdapter<T> {
    /**
     * Deserialize raw YAML/JSON value into target type.
     *
     * @param value raw value (String/Map/List/etc.)
     * @return deserialized instance or null if value cannot be parsed
     */
    T deserialize(Object value);

    /**
     * Serialize target type back to a YAML-friendly structure.
     *
     * @param value value to serialize
     * @return raw representation (String/Map/List/etc.)
     */
    Object serialize(T value);
}
