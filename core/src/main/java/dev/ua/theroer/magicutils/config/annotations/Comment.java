package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Adds a comment to a configuration field in the generated YAML/JSONC/TOML file.
 * Comments are ignored for formats that do not support them.
 */
@Target({ ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Comment {
    /**
     * Comment text. Can be multiline.
     * 
     * @return the comment text
     */
    String value();

    /**
     * Whether to place comment above (true) or inline (false).
     * 
     * @return true for above, false for inline
     */
    boolean above() default true;
}
