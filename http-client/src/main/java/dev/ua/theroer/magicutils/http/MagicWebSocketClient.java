package dev.ua.theroer.magicutils.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.Tasks;
import dev.ua.theroer.magicutils.platform.ThreadContext;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * MagicUtils WebSocket client with config defaults and logging.
 */
public final class MagicWebSocketClient implements AutoCloseable {
    private static final String HEADER_USER_AGENT = "User-Agent";

    private final HttpClient client;
    private final HttpClientConfig config;
    private final PlatformLogger logger;
    private final HttpClientConfig.LoggingSettings logging;
    private final String baseUrl;
    private final Duration connectTimeout;
    private final Map<String, String> defaultHeaders;
    private final Platform platform;
    private final List<String> subprotocols;
    private final boolean ownsClient;

    private MagicWebSocketClient(Builder builder) {
        this.platform = builder.platform;
        this.config = builder.config != null ? builder.config : new HttpClientConfig();
        this.logger = builder.logger;
        this.logging = builder.logging != null ? builder.logging : config.getLogging();
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : config.getDefaults().getBaseUrl();
        this.connectTimeout = builder.connectTimeout != null
                ? builder.connectTimeout
                : toDuration(config.getTimeouts().getConnectSeconds());
        this.defaultHeaders = buildDefaultHeaders(builder);
        this.subprotocols = builder.subprotocols;
        this.ownsClient = builder.client == null;
        this.client = builder.client != null ? builder.client : buildHttpClient(builder);
    }

    /**
     * Creates a builder that loads configuration from the provided manager.
     *
     * @param platform platform used for logging
     * @param configManager configuration manager used to register {@link HttpClientConfig}
     * @return a new builder instance
     */
    public static Builder builder(Platform platform, ConfigManager configManager) {
        return new Builder(platform, configManager);
    }

    /**
     * Creates a builder without auto-loading configuration.
     *
     * @param platform platform used for logging
     * @return a new builder instance
     */
    public static Builder builder(Platform platform) {
        return new Builder(platform, null);
    }

    /**
     * Connects to a WebSocket endpoint asynchronously.
     *
     * @param path absolute URL or a path resolved against base URL
     * @param listener websocket listener
     * @return future with WebSocket
     */
    public CompletableFuture<WebSocket> connectAsync(String path, WebSocket.Listener listener) {
        Objects.requireNonNull(listener, "listener");
        URI uri = resolveWebSocketUri(path);
        WebSocket.Builder wsBuilder = client.newWebSocketBuilder();
        if (connectTimeout != null) {
            wsBuilder.connectTimeout(connectTimeout);
        }
        if (subprotocols != null && !subprotocols.isEmpty()) {
            String first = subprotocols.get(0);
            String[] rest = subprotocols.size() > 1
                    ? subprotocols.subList(1, subprotocols.size()).toArray(String[]::new)
                    : new String[0];
            wsBuilder.subprotocols(first, rest);
        }
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                wsBuilder.header(entry.getKey(), entry.getValue());
            }
        }
        WebSocket.Listener wrapped = wrapListener(listener, uri);
        logConnect(uri);
        return wsBuilder.buildAsync(uri, wrapped);
    }

    /**
     * Connects to a WebSocket endpoint (blocking).
     *
     * @param path absolute URL or a path resolved against base URL
     * @param listener websocket listener
     * @return connected WebSocket
     * @throws IOException if connection fails
     * @throws InterruptedException if thread interrupted
     */
    public WebSocket connect(String path, WebSocket.Listener listener) throws IOException, InterruptedException {
        checkNotMainThread("connect");
        try {
            return connectAsync(path, listener).get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = unwrap(e);
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("WebSocket connect failed", cause);
        }
    }

    /**
     * Connects to a WebSocket endpoint, switching to async on blocking-sensitive threads.
     *
     * @param path absolute URL or a path resolved against base URL
     * @param listener websocket listener
     * @return future with WebSocket
     */
    public CompletableFuture<WebSocket> connectSmart(String path, WebSocket.Listener listener) {
        return smartCall("connect", () -> connect(path, listener), () -> connectAsync(path, listener));
    }

    @Override
    public void close() {
        if (!ownsClient) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Failed to close WebSocket HTTP client", e);
            }
        }
    }

    private boolean isBlockingSensitiveThread() {
        if (platform == null) {
            return false;
        }
        ThreadContext context = platform.threadContext();
        if (context == ThreadContext.UNKNOWN) {
            return platform.isMainThread();
        }
        return context.isBlockingSensitive();
    }

    private void checkNotMainThread(String method) {
        if (isBlockingSensitiveThread()) {
            throw new IllegalStateException(
                    "Synchronous WebSocket call '" + method + "' is not allowed on a blocking-sensitive thread. " +
                            "Use the ...Async() variant instead to avoid server/client freezes."
            );
        }
    }

    private <T> CompletableFuture<T> smartCall(String method,
                                               Callable<T> blockingCall,
                                               Supplier<CompletableFuture<T>> asyncCall) {
        if (isBlockingSensitiveThread()) {
            return asyncCall.get();
        }
        try {
            return CompletableFuture.completedFuture(blockingCall.call());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Tasks.failedFuture(e);
        } catch (Exception e) {
            return Tasks.failedFuture(e);
        }
    }

    private Map<String, String> buildDefaultHeaders(Builder builder) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.getDefaults().getHeaders() != null) {
            headers.putAll(config.getDefaults().getHeaders());
        }
        if (builder.defaultHeaders != null) {
            headers.putAll(builder.defaultHeaders);
        }
        String agent = builder.userAgent != null ? builder.userAgent : config.getDefaults().getUserAgent();
        if (agent != null && !agent.isBlank()) {
            headers.putIfAbsent(HEADER_USER_AGENT, agent);
        }
        return headers;
    }

    private HttpClient buildHttpClient(Builder builder) {
        HttpClient.Builder httpBuilder = builder.httpBuilder != null ? builder.httpBuilder : HttpClient.newBuilder();
        Duration timeout = builder.connectTimeout != null
                ? builder.connectTimeout
                : toDuration(config.getTimeouts().getConnectSeconds());
        if (timeout != null) {
            httpBuilder.connectTimeout(timeout);
        }
        HttpClient.Version version = builder.version != null
                ? builder.version
                : parseVersion(config.getDefaults().getHttpVersion());
        if (version != null) {
            httpBuilder.version(version);
        }
        Boolean follow = builder.followRedirects != null
                ? builder.followRedirects
                : config.getDefaults().isFollowRedirects();
        httpBuilder.followRedirects(Boolean.TRUE.equals(follow) ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
        return httpBuilder.build();
    }

    private HttpClient.Version parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase();
        if ("HTTP_1_1".equals(normalized) || "HTTP1_1".equals(normalized)) {
            return HttpClient.Version.HTTP_1_1;
        }
        if ("HTTP_2".equals(normalized) || "HTTP2".equals(normalized)) {
            return HttpClient.Version.HTTP_2;
        }
        return null;
    }

    private Duration toDuration(int seconds) {
        if (seconds <= 0) {
            return null;
        }
        return Duration.ofSeconds(seconds);
    }

    private URI resolveWebSocketUri(String path) {
        URI uri = resolveUri(path);
        return normalizeWebSocketUri(uri);
    }

    private URI resolveUri(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is empty");
        }
        URI resolved = URI.create(path);
        if (resolved.isAbsolute()) {
            return resolved;
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            URI base = URI.create(baseUrl);
            if (!path.startsWith("/")
                    && base.getPath() != null
                    && !base.getPath().isEmpty()
                    && !base.getPath().endsWith("/")) {
                base = URI.create(base.toString() + "/");
            }
            return base.resolve(resolved);
        }
        return resolved;
    }

    private URI normalizeWebSocketUri(URI uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return uri;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return replaceScheme(uri, "ws");
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return replaceScheme(uri, "wss");
        }
        return uri;
    }

    private URI replaceScheme(URI uri, String scheme) {
        try {
            return new URI(
                    scheme,
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        } catch (URISyntaxException e) {
            return uri;
        }
    }

    private void logConnect(URI uri) {
        if (!isLoggingEnabled() || logger == null || uri == null) {
            return;
        }
        logger.info("[WS] Connect " + uri);
    }

    private WebSocket.Listener wrapListener(WebSocket.Listener listener, URI uri) {
        if (!isLoggingEnabled() || logger == null) {
            return listener;
        }
        return new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                logger.info("[WS] Open " + uri);
                listener.onOpen(webSocket);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                return listener.onText(webSocket, data, last);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onBinary(WebSocket webSocket, java.nio.ByteBuffer data, boolean last) {
                return listener.onBinary(webSocket, data, last);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onPing(WebSocket webSocket, java.nio.ByteBuffer message) {
                return listener.onPing(webSocket, message);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onPong(WebSocket webSocket, java.nio.ByteBuffer message) {
                return listener.onPong(webSocket, message);
            }

            @Override
            public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                logger.info("[WS] Close " + uri + " code=" + statusCode + " reason=" + reason);
                return listener.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                logger.warn("[WS] Error " + uri + ": " + (error != null ? error.getMessage() : "unknown"), error);
                listener.onError(webSocket, error);
            }
        };
    }

    private boolean isLoggingEnabled() {
        return logging != null && logging.isEnabled();
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    /**
     * Builder for {@link MagicWebSocketClient}.
     */
    public static final class Builder {
        private final Platform platform;
        private PlatformLogger logger;
        private HttpClientConfig config;
        private HttpClientConfig.LoggingSettings logging;
        private ObjectMapper mapper;
        private String baseUrl;
        private String userAgent;
        private Map<String, String> defaultHeaders;
        private Duration connectTimeout;
        private Boolean followRedirects;
        private HttpClient.Version version;
        private HttpClient client;
        private HttpClient.Builder httpBuilder;
        private List<String> subprotocols;

        private Builder(Platform platform, ConfigManager configManager) {
            this.platform = platform;
            this.logger = platform != null ? platform.logger() : null;
            if (configManager != null) {
                this.config = configManager.register(HttpClientConfig.class);
            }
        }

        /**
         * Overrides logger used for connection logging.
         *
         * @param logger platform logger
         * @return this builder
         */
        public Builder logger(PlatformLogger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Overrides the configuration instance.
         *
         * @param config configuration to use
         * @return this builder
         */
        public Builder config(HttpClientConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Overrides logging settings.
         *
         * @param logging logging settings
         * @return this builder
         */
        public Builder logging(HttpClientConfig.LoggingSettings logging) {
            this.logging = logging;
            return this;
        }

        /**
         * Overrides JSON mapper (reserved for future extensions).
         *
         * @param mapper object mapper
         * @return this builder
         */
        public Builder mapper(ObjectMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        /**
         * Sets a base URL used for relative paths.
         *
         * @param baseUrl base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets default User-Agent header value.
         *
         * @param userAgent user agent value
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Adds a default header.
         *
         * @param name header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            if (defaultHeaders == null) {
                defaultHeaders = new LinkedHashMap<>();
            }
            defaultHeaders.put(name, value);
            return this;
        }

        /**
         * Adds default headers.
         *
         * @param headers header map
         * @return this builder
         */
        public Builder headers(Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) {
                return this;
            }
            if (defaultHeaders == null) {
                defaultHeaders = new LinkedHashMap<>();
            }
            defaultHeaders.putAll(headers);
            return this;
        }

        /**
         * Sets connect timeout.
         *
         * @param timeout connect timeout
         * @return this builder
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Sets follow-redirects behavior.
         *
         * @param follow follow redirects
         * @return this builder
         */
        public Builder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }

        /**
         * Sets HTTP version.
         *
         * @param version http version
         * @return this builder
         */
        public Builder version(HttpClient.Version version) {
            this.version = version;
            return this;
        }

        /**
         * Sets subprotocols for the connection.
         *
         * @param subprotocols list of subprotocols
         * @return this builder
         */
        public Builder subprotocols(List<String> subprotocols) {
            this.subprotocols = subprotocols;
            return this;
        }

        /**
         * Overrides the HttpClient instance.
         *
         * @param client custom HttpClient
         * @return this builder
         */
        public Builder client(HttpClient client) {
            this.client = client;
            return this;
        }

        /**
         * Overrides the HttpClient builder.
         *
         * @param builder custom HttpClient builder
         * @return this builder
         */
        public Builder httpBuilder(HttpClient.Builder builder) {
            this.httpBuilder = builder;
            return this;
        }

        /**
         * Builds the client.
         *
         * @return MagicWebSocketClient
         */
        public MagicWebSocketClient build() {
            return new MagicWebSocketClient(this);
        }
    }
}
