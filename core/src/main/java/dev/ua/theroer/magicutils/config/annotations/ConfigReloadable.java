package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Marks which sections of a config can be hot-reloaded.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigReloadable {
    /**
     * List of section paths that can be reloaded.
     * Empty array means all sections can be reloaded.
     * 
     * @return array of reloadable sections
     */
    String[] sections() default {};

    /**
     * Whether to trigger reload listeners on change.
     * 
     * @return true to trigger listeners
     */
    boolean notifyOnChange() default true;
}