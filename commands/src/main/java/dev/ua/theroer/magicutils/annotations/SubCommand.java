package dev.ua.theroer.magicutils.annotations;

import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking a method as a subcommand handler.
 * Used to specify subcommand name, description, aliases, and permission
 * requirement.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {
    /**
     * Subcommand name.
     * 
     * @return the name of the subcommand
     */
    String name();

    /**
     * Optional parent path segments for nested subcommands.
     *
     * @return parent path segments
     */
    String[] path() default {};

    /**
     * Subcommand description.
     * 
     * @return the description of the subcommand
     */
    String description() default "";

    /**
     * Subcommand aliases.
     * 
     * @return the aliases for the subcommand
     */
    String[] aliases() default {};

    /**
     * Specific permission node required to execute the subcommand. If empty, no check is performed.
     *
     * @return permission node
     */
    String permission() default "";

    /**
     * Default permission state (matches Bukkit PermissionDefault).
     *
     * @return default permission
     */
    MagicPermissionDefault permissionDefault() default MagicPermissionDefault.OP;
}
