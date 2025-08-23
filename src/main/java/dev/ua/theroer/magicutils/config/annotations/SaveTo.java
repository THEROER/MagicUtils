package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Specifies that a field should be saved to a different file.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SaveTo {
    /**
     * Path to the file where this field should be saved.
     * Relative to plugin's data folder.
     * @return the file path
     */
    String value();
}