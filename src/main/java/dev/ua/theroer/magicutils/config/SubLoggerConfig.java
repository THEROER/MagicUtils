package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Sub-logger configuration.
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