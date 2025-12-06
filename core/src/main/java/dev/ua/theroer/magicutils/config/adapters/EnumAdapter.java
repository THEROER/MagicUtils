package dev.ua.theroer.magicutils.config.adapters;

import dev.ua.theroer.magicutils.config.serialization.ConfigValueAdapter;

/**
 * Generic adapter for enums using their name() for serialization.
 *
 * @param <E> enum type
 */
public class EnumAdapter<E extends Enum<E>> implements ConfigValueAdapter<E> {
    private final Class<E> enumClass;

    /**
     * Create an adapter for the given enum class.
     *
     * @param enumClass enum class to adapt
     */
    public EnumAdapter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public E deserialize(Object value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, String.valueOf(value).toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public Object serialize(E value) {
        return value != null ? value.name() : null;
    }
}
