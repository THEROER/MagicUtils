package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Marks a class as serializable for configuration.
 * Classes with this annotation can be used in lists and maps within configs.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigSerializable {
    /**
     * Whether to include null fields when serializing.
     * 
     * @return true to include nulls
     */
    boolean includeNulls() default false;
}