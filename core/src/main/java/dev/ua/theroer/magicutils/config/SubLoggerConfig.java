package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;

/**
 * Sub-logger configuration.
 * <p>
 * This class represents the configuration settings for a sub-logger,
 * allowing control over whether the sub-logger is enabled or disabled.
 * </p>
 * 
 * <p>
 * Constructor usage:
 * </p>
 * <ul>
 * <li>{@link #SubLoggerConfig()} - Creates a default configuration</li>
 * <li>SubLoggerConfig(boolean) - Creates configuration with specified enabled
 * state</li>
 * </ul>
 */
@ConfigSerializable
@Data
@Builder
@AllArgsConstructor
public class SubLoggerConfig {
    /**
     * Default constructor for SubLoggerConfig.
     */
    public SubLoggerConfig() {
    }

    @Comment("Whether this sub-logger is enabled")
    @Builder.Default
    private boolean enabled = true;
}
