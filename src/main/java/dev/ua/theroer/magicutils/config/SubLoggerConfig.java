package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Sub-logger configuration.
 * <p>
 * This class represents the configuration settings for a sub-logger,
 * allowing control over whether the sub-logger is enabled or disabled.
 * </p>
 * 
 * <p>Constructor usage:</p>
 * <ul>
 * <li>{@link #SubLoggerConfig()} - Creates a default configuration</li>
 * <li>SubLoggerConfig(boolean) - Creates configuration with specified enabled state</li>
 * </ul>
 */
@ConfigSerializable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubLoggerConfig {
    @Comment("Whether this sub-logger is enabled")
    private boolean enabled;
}