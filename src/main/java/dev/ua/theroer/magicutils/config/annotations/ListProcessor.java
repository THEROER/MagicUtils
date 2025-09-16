package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Specifies a processor for list values.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ListProcessor {
    /**
     * Processor class that implements ListItemProcessor.
     * 
     * @return the processor class
     */
    Class<? extends ListItemProcessor> value();
}