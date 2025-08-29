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
     * @return array of log levels for which to generate methods
     */
    LogLevel[] levels() default {LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.DEBUG, LogLevel.SUCCESS};
    
    /**
     * Whether to generate static methods
     * @return true if methods should be static, false otherwise
     */
    boolean staticMethods() default true;
    
    /**
     * Log levels enum for annotation
     */
    enum LogLevel {
        /** Information level logging */
        INFO,
        /** Warning level logging */
        WARN,
        /** Error level logging */
        ERROR,
        /** Debug level logging */
        DEBUG,
        /** Success level logging */
        SUCCESS
    }
}