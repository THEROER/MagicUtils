package dev.ua.theroer.magicutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify a default value for a command argument.
 * <p>
 * <strong>Note:</strong> This is for the command framework. For config/POJO defaults,
 * use {@code dev.ua.theroer.magicutils.config.annotations.DefaultValue} instead.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultValue {
    /**
     * The default value for the argument.
     * 
     * @return the default value
     */
    String value();
}
