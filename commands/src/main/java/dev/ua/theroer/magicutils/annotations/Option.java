package dev.ua.theroer.magicutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a parameter as a named option (e.g. --amount, -a).
 * Options can be used alongside positional arguments.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Option {
    /**
     * Short option names without "-" prefix.
     *
     * @return short option names
     */
    String[] shortNames() default {};

    /**
     * Long option names without "--" prefix.
     *
     * @return long option names
     */
    String[] longNames() default {};

    /**
     * Marks this option as a flag (no value required).
     *
     * @return true when this is a flag
     */
    boolean flag() default false;
}
