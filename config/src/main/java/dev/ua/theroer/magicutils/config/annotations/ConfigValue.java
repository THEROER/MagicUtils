package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Marks a field as a configuration value.
 * The field will be automatically populated from the config file.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigValue {
    /**
     * Path to the value in the configuration file.
     * Supports dot notation for nested values (e.g., "database.host").
     * If empty, uses the field name.
     * 
     * @return the configuration path
     */
    String value() default "";

    /**
     * Whether this value is required.
     * 
     * @return true if required, false otherwise
     */
    boolean required() default false;
}