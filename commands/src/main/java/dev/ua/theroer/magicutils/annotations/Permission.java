package dev.ua.theroer.magicutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.ua.theroer.magicutils.commands.CompareMode;
import dev.ua.theroer.magicutils.commands.PermissionConditionType;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;

/**
 * Annotation to specify permission requirements for commands or arguments.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {
    /**
     * The required permission string.
     * 
     * @return the permission string
     */
    String value() default "";

    /**
     * The condition when the permission is required.
     * 
     * @return the condition string
     */
    String when() default "";

    /**
     * Structured condition type when the permission is required.
     *
     * @return condition type
     */
    PermissionConditionType condition() default PermissionConditionType.ALWAYS;

    /**
     * Override for generated permission node segment (defaults to argument name).
     *
     * @return custom node segment
     */
    String node() default "";

    /**
     * Whether to include ".argument." segment before the node (defaults to true).
     *
     * @return true to include ".argument."
     */
    boolean includeArgumentSegment() default true;

    /**
     * Names of arguments that participate in the condition.
     *
     * @return argument names
     */
    String[] conditionArgs() default {};

    /**
     * How to compare values (name/UUID/equals/auto).
     *
     * @return compare mode
     */
    CompareMode compare() default CompareMode.AUTO;

    /**
     * The message to display if permission is denied.
     * 
     * @return the permission denied message
     */
    String message() default "magicutils.noPermission";

    /**
     * Default permission state (string name mirroring Bukkit PermissionDefault).
     *
     * @return default permission
     */
    MagicPermissionDefault defaultValue() default MagicPermissionDefault.OP;
}
