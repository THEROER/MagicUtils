package dev.ua.theroer.magicutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify permission requirements for commands or arguments.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {
    /**
     * The required permission string.
     * @return the permission string
     */
    String value() default "";
    /**
     * The condition when the permission is required.
     * @return the condition string
     */
    String when() default "";
    /**
     * The message to display if permission is denied.
     * @return the permission denied message
     */
    String message() default "magicutils.noPermission";
}