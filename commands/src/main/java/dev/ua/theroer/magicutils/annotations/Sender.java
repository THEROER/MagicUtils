package dev.ua.theroer.magicutils.annotations;

import dev.ua.theroer.magicutils.commands.AllowedSender;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a command parameter as the executing sender.
 * The argument is auto-filled and not taken from user input.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sender {
    /**
     * Restrict allowed sender types. Defaults to any.
     *
     * @return allowed sender kinds
     */
    AllowedSender[] value() default {AllowedSender.ANY};
}
