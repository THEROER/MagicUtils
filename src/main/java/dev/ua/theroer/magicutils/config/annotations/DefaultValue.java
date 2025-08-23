package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Specifies default value for a configuration field.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DefaultValue {
    /**
     * Default value as string. Will be converted to the field type.
     * @return the default value
     */
    String value() default "";
    
    /**
     * Provider class for complex default values.
     * Must implement DefaultValueProvider interface.
     * @return the provider class
     */
    Class<? extends DefaultValueProvider> provider() default DefaultValueProvider.class;
}