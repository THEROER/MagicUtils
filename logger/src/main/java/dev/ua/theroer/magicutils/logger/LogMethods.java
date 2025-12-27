package dev.ua.theroer.magicutils.logger;

import java.lang.annotation.*;

/**
 * Annotation to generate logging methods for specified log levels
 * Similar to Lombok's @Getter but for logging methods
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface LogMethods {
    /**
     * Log levels to generate methods for
     * 
     * @return array of log levels for which to generate methods
     */
    LogLevel[] levels() default { LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.DEBUG, LogLevel.SUCCESS, LogLevel.TRACE };

    /**
     * Whether to generate static methods
     * 
     * @return true if methods should be static, false otherwise
     */
    boolean staticMethods() default true;

    /**
     * Audience type used for player-targeted overloads.
     *
     * @return fully-qualified type name for audience/player parameters
     */
    String audienceType() default "dev.ua.theroer.magicutils.platform.Audience";
}
