package dev.ua.theroer.magicutils.messaging.redis;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Data;

/**
 * Configuration for the optional Redis messaging transport.
 *
 * <p>Stored in {@code messaging.{ext}}. When {@code enabled} is false the
 * messaging service falls back to the default plugin-messaging transport, so a
 * network can run entirely without Redis.</p>
 */
@ConfigFile("messaging.{ext}")
@ConfigReloadable(sections = { "redis" })
@Comment("MagicUtils cross-server messaging configuration")
@Data
public class RedisConfig {
    /**
     * Creates a config instance with default values.
     */
    public RedisConfig() {
    }

    @ConfigSection("redis")
    @Comment("Redis pub/sub transport. When disabled, the default plugin-messaging transport is used instead.")
    private Redis redis = new Redis();

    /**
     * Redis connection and channel settings.
     */
    @Data
    public static class Redis {
        /**
         * Creates default Redis settings.
         */
        public Redis() {
        }

        @ConfigValue("enabled")
        @Comment("Use Redis for cross-server messaging. If false, plugin messaging is used.")
        private boolean enabled = false;

        @ConfigValue("host")
        @Comment("Redis host")
        private String host = "127.0.0.1";

        @ConfigValue("port")
        @Comment("Redis port")
        private int port = 6379;

        @ConfigValue("username")
        @Comment("Redis username (ACL); leave blank for legacy AUTH")
        private String username = "";

        @ConfigValue("password")
        @Comment("Redis password; leave blank when unauthenticated")
        private String password = "";

        @ConfigValue("database")
        @Comment("Redis database index")
        private int database = 0;

        @ConfigValue("ssl")
        @Comment("Connect using TLS")
        private boolean ssl = false;

        @ConfigValue("timeout-millis")
        @Comment("Socket/connect timeout in milliseconds")
        private int timeoutMillis = 2000;

        @ConfigValue("channel")
        @Comment("Redis pub/sub channel all MagicUtils members share")
        private String channel = "magicutils:bus";

        @ConfigValue("pool-max-total")
        @Comment("Maximum pooled connections")
        private int poolMaxTotal = 8;
    }
}
