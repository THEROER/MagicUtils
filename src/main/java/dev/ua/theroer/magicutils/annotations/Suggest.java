package dev.ua.theroer.magicutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide autocomplete suggestions for command arguments.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Suggest {
    /**
     * Sources of suggestions for autocomplete.
     * Can contain method names, special arguments, static lists, or combined sources.
     * @return the suggestion sources
     */
    String[] value();
    
    /**
     * Whether permission is required to display suggestions.
     * If true, permission will be checked for each suggestion.
     * @return true if permission is required for suggestions
     */
    boolean permission() default false;
}