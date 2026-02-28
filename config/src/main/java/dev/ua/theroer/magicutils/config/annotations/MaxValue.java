package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Specifies the maximum value for numeric configuration fields.
 * When a config value is loaded that is greater than the maximum,
 * it will be automatically clamped to the maximum value.
 *
 * <p>Supports: byte, short, int, long, float, double and their wrapper types.
 *
 * <p>Example:
 * <pre>
 * {@code @ConfigValue("max_players")
 * @MaxValue(100)
 * @Comment("Maximum players (maximum: 100)")
 * private int maxPlayers = 20;}
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaxValue {
    /**
     * The maximum allowed value for this field.
     *
     * @return the maximum value
     */
    double value();

    /**
     * Whether to log a warning when a value is clamped.
     *
     * @return true to log warnings, false otherwise
     */
    boolean warn() default true;
}
