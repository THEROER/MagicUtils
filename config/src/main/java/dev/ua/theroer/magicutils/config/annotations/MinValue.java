package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Specifies the minimum value for numeric configuration fields.
 * When a config value is loaded that is less than the minimum,
 * it will be automatically clamped to the minimum value.
 *
 * <p>Supports: byte, short, int, long, float, double and their wrapper types.
 *
 * <p>Example:
 * <pre>
 * {@code @ConfigValue("retry_interval")
 * @MinValue(5)
 * @Comment("Retry interval in seconds (minimum: 5)")
 * private int retryInterval = 10;}
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MinValue {
    /**
     * The minimum allowed value for this field.
     *
     * @return the minimum value
     */
    double value();

    /**
     * Whether to log a warning when a value is clamped.
     *
     * @return true to log warnings, false otherwise
     */
    boolean warn() default true;
}
