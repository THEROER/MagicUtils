package dev.ua.theroer.magicutils.annotations;

import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for command metadata.
 * Used to specify command name, description, aliases, and permission
 * requirement.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandInfo {
    /**
     * Command name.
     * 
     * @return the name of the command
     */
    String name();

    /**
     * Command description.
     * 
     * @return the description of the command
     */
    String description() default "";

    /**
     * Command aliases.
     * 
     * @return the aliases for the command
     */
    String[] aliases() default {};

    /**
     * Specific permission node required to execute the command. If empty, no check is performed.
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
