package dev.ua.theroer.magicutils.config.annotations;

import java.lang.annotation.*;

/**
 * Marks a class as a configuration file.
 * The class will be automatically loaded from and saved to the specified file.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigFile {
    /**
     * Path to the configuration file relative to plugin's data folder.
     * @return the file path
     */
    String value();
    
    /**
     * Whether to create the file if it doesn't exist.
     * @return true to auto-create, false otherwise
     */
    boolean autoCreate() default true;
    
    /**
     * Template file to use when creating new config.
     * @return path to template file in resources
     */
    String template() default "";
}