package dev.ua.theroer.magicutils.http;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for MagicUtils HTTP client.
 *
 * <p>Stored in {@code http-client.{ext}} and reloadable per section.
 */
@ConfigFile("http-client.{ext}")
@ConfigReloadable(sections = { "timeouts", "retry", "logging", "defaults" })
@Comment("MagicUtils HTTP client configuration")
@Data
public class HttpClientConfig {
    /**
     * Creates a config instance with default values.
     */
    public HttpClientConfig() {
    }

    @ConfigSection("timeouts")
    @Comment("Timeout configuration")
    private Timeouts timeouts = new Timeouts();

    @ConfigSection("retry")
    @Comment("Retry and backoff configuration")
    private RetrySettings retry = new RetrySettings();

    @ConfigSection("logging")
    @Comment("Request/response logging")
    private LoggingSettings logging = new LoggingSettings();

    @ConfigSection("defaults")
    @Comment("Default request settings")
    private DefaultSettings defaults = new DefaultSettings();

    /**
     * Timeouts used for HTTP connection and request handling.
     */
    @Data
    public static class Timeouts {
        /**
         * Creates default timeout settings.
         */
        public Timeouts() {
        }

        @ConfigValue("connect-seconds")
        @Comment("HTTP connect timeout in seconds")
        private int connectSeconds = 10;

        @ConfigValue("request-seconds")
        @Comment("HTTP request timeout in seconds")
        private int requestSeconds = 30;
    }

    /**
     * Retry and backoff configuration values.
     */
    @Data
    public static class RetrySettings {
        /**
         * Creates default retry settings.
         */
        public RetrySettings() {
        }

        @ConfigValue("enabled")
        @Comment("Enable retries for failed requests")
        private boolean enabled = true;

        @ConfigValue("max-attempts")
        @Comment("Max attempts per request (including the first attempt)")
        private int maxAttempts = 3;

        @ConfigValue("initial-delay-ms")
        @Comment("Initial backoff delay in milliseconds")
        private long initialDelayMs = 250;

        @ConfigValue("max-delay-ms")
        @Comment("Maximum backoff delay in milliseconds")
        private long maxDelayMs = 2_000;

        @ConfigValue("multiplier")
        @Comment("Backoff multiplier")
        private double multiplier = 2.0;

        @ConfigValue("jitter")
        @Comment("Jitter factor (0.0 - 1.0)")
        private double jitter = 0.2;

        @ConfigValue("retry-on-status")
        @Comment("HTTP status codes that should be retried")
        private List<Integer> retryStatus = new ArrayList<>(List.of(429, 500, 502, 503, 504));

        @ConfigValue("retry-on-io")
        @Comment("Retry on IO exceptions")
        private boolean retryOnIo = true;

        @ConfigValue("retry-on-timeout")
        @Comment("Retry on request timeouts")
        private boolean retryOnTimeout = true;
    }

    /**
     * Request/response logging configuration.
     */
    @Data
    public static class LoggingSettings {
        /**
         * Creates default logging settings.
         */
        public LoggingSettings() {
        }

        @ConfigValue("enabled")
        @Comment("Enable request/response logging")
        private boolean enabled = false;

        @ConfigValue("log-headers")
        @Comment("Log request/response headers")
        private boolean logHeaders = false;

        @ConfigValue("log-body")
        @Comment("Log request/response bodies (string only)")
        private boolean logBody = false;

        @ConfigValue("max-body-length")
        @Comment("Max body length to log")
        private int maxBodyLength = 2048;

        @ConfigValue("log-retries")
        @Comment("Log retry attempts")
        private boolean logRetries = true;

        @ConfigValue("sensitive-headers")
        @Comment("List of headers that should be redacted from logs (e.g., Authorization, Cookie)")
        private List<String> sensitiveHeaders = new ArrayList<>(List.of("Authorization", "Cookie"));
    }

    /**
     * Default request settings applied to each request.
     */
    @Data
    public static class DefaultSettings {
        /**
         * Creates default request settings.
         */
        public DefaultSettings() {
        }

        @ConfigValue("base-url")
        @Comment("Base URL used for relative request paths")
        private String baseUrl = "";

        @ConfigValue("user-agent")
        @Comment("Default User-Agent header")
        private String userAgent = "MagicUtils-HttpClient";

        @ConfigValue("follow-redirects")
        @Comment("Follow redirects automatically")
        private boolean followRedirects = true;

        @ConfigValue("http-version")
        @Comment("HTTP version: HTTP_1_1 or HTTP_2")
        private String httpVersion = "HTTP_2";

        @ConfigValue("headers")
        @Comment("Default headers applied to every request")
        private Map<String, String> headers = new LinkedHashMap<>();
    }
}
