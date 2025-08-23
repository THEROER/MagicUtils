package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Marks a field as a configuration section.
 * The field type should be another configuration class.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigSection {
    /**
     * Path to the section in the configuration file.
     * If empty, uses the field name.
     * @return the section path
     */
    String value() default "";
}