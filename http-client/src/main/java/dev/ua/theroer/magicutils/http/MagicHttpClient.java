package dev.ua.theroer.magicutils.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MagicUtils HTTP client with retries, config defaults, and JSON mapping.
 */
public final class MagicHttpClient implements AutoCloseable {
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final HttpClientConfig config;
    private final RetryPolicy retryPolicy;
    private final PlatformLogger logger;
    private final HttpClientConfig.LoggingSettings logging;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final Map<String, String> defaultHeaders;

    private MagicHttpClient(Builder builder) {
        this.config = builder.config != null ? builder.config : new HttpClientConfig();
        this.logger = builder.logger;
        this.mapper = builder.mapper != null ? builder.mapper : defaultMapper();
        this.logging = builder.logging != null ? builder.logging : config.getLogging();
        this.retryPolicy = builder.retryPolicy != null ? builder.retryPolicy : RetryPolicy.fromConfig(config.getRetry());
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : config.getDefaults().getBaseUrl();
        this.requestTimeout = builder.requestTimeout != null ? builder.requestTimeout : toDuration(config.getTimeouts().getRequestSeconds());
        this.defaultHeaders = buildDefaultHeaders(builder);
        this.client = builder.client != null ? builder.client : buildHttpClient(builder);
    }

    public static Builder builder(Platform platform, ConfigManager configManager) {
        return new Builder(platform, configManager);
    }

    public static Builder builder(Platform platform) {
        return new Builder(platform, null);
    }

    public HttpClientConfig config() {
        return config;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public HttpRequest.Builder request(String path) {
        URI uri = resolveUri(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if (requestTimeout != null) {
            builder.timeout(requestTimeout);
        }
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return builder;
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return sendWithRetries(() -> request(path).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResponse<String>> getAsync(String path) {
        return sendWithRetriesAsync(() -> request(path).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public <T> T getJson(String path, Class<T> type) throws IOException, InterruptedException {
        HttpResponse<String> response = get(path);
        return readJson(response.body(), type);
    }

    public <T> CompletableFuture<T> getJsonAsync(String path, Class<T> type) {
        return getAsync(path).thenApply(response -> readJson(response.body(), type));
    }

    public HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = request(path)
                .header(HEADER_CONTENT_TYPE, "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "", StandardCharsets.UTF_8))
                .build();
        logRequest(request, body);
        return sendWithRetries(() -> request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResponse<String>> postAsync(String path, String body) {
        HttpRequest request = request(path)
                .header(HEADER_CONTENT_TYPE, "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "", StandardCharsets.UTF_8))
                .build();
        logRequest(request, body);
        return sendWithRetriesAsync(() -> request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> postJson(String path, Object body) throws IOException, InterruptedException {
        String payload = writeJson(body);
        HttpRequest request = request(path)
                .header(HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
                .header(HEADER_ACCEPT, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        logRequest(request, payload);
        return sendWithRetries(() -> request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public <T> T postJson(String path, Object body, Class<T> type) throws IOException, InterruptedException {
        HttpResponse<String> response = postJson(path, body);
        return readJson(response.body(), type);
    }

    public CompletableFuture<HttpResponse<String>> postJsonAsync(String path, Object body) {
        String payload = writeJson(body);
        HttpRequest request = request(path)
                .header(HEADER_CONTENT_TYPE, JSON_CONTENT_TYPE)
                .header(HEADER_ACCEPT, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        logRequest(request, payload);
        return sendWithRetriesAsync(() -> request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public <T> CompletableFuture<T> postJsonAsync(String path, Object body, Class<T> type) {
        return postJsonAsync(path, body).thenApply(response -> readJson(response.body(), type));
    }

    public HttpResponse<String> postMultipart(String path, MultipartBody multipart) throws IOException, InterruptedException {
        Objects.requireNonNull(multipart, "multipart");
        HttpRequest request = request(path)
                .header(HEADER_CONTENT_TYPE, multipart.contentType())
                .POST(multipart.publisher())
                .build();
        logRequest(request, null);
        return sendWithRetries(() -> request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public CompletableFuture<HttpResponse<String>> postMultipartAsync(String path, MultipartBody multipart) {
        Objects.requireNonNull(multipart, "multipart");
        HttpRequest request = request(path)
                .header(HEADER_CONTENT_TYPE, multipart.contentType())
                .POST(multipart.publisher())
                .build();
        logRequest(request, null);
        return sendWithRetriesAsync(() -> request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<Path> downloadToFile(String path, Path target) throws IOException, InterruptedException {
        HttpRequest request = request(path).GET().build();
        logRequest(request, null);
        return sendWithRetries(() -> request, HttpResponse.BodyHandlers.ofFile(target));
    }

    public CompletableFuture<HttpResponse<Path>> downloadToFileAsync(String path, Path target) {
        HttpRequest request = request(path).GET().build();
        logRequest(request, null);
        return sendWithRetriesAsync(() -> request, HttpResponse.BodyHandlers.ofFile(target));
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        logRequest(request, null);
        HttpResponse<T> response = client.send(request, handler);
        logResponse(response);
        return response;
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        logRequest(request, null);
        return client.sendAsync(request, handler).whenComplete((response, error) -> {
            if (error == null) {
                logResponse(response);
            }
        });
    }

    @Override
    public void close() {
        // no resources to release yet
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
        Duration connectTimeout = builder.connectTimeout != null
                ? builder.connectTimeout
                : toDuration(config.getTimeouts().getConnectSeconds());
        if (connectTimeout != null) {
            httpBuilder.connectTimeout(connectTimeout);
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

    private URI resolveUri(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is empty");
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            return URI.create(baseUrl).resolve(path);
        }
        return URI.create(path);
    }

    private ObjectMapper defaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    private String writeJson(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse JSON", e);
        }
    }

    private <T> HttpResponse<T> sendWithRetries(Supplier<HttpRequest> supplier, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(handler, "handler");
        int attempt = 1;
        while (true) {
            HttpRequest request = supplier.get();
            try {
                HttpResponse<T> response = client.send(request, handler);
                logResponse(response);
                if (!retryPolicy.shouldRetryStatus(response.statusCode()) || attempt >= retryPolicy.maxAttempts()) {
                    return response;
                }
            } catch (HttpTimeoutException e) {
                if (!retryPolicy.shouldRetryThrowable(e) || attempt >= retryPolicy.maxAttempts()) {
                    throw e;
                }
            } catch (IOException e) {
                if (!retryPolicy.shouldRetryThrowable(e) || attempt >= retryPolicy.maxAttempts()) {
                    throw e;
                }
            }
            if (logRetry(attempt, request)) {
                // logged
            }
            sleep(retryPolicy.nextDelayMs(attempt));
            attempt++;
        }
    }

    private <T> CompletableFuture<HttpResponse<T>> sendWithRetriesAsync(Supplier<HttpRequest> supplier,
                                                                        HttpResponse.BodyHandler<T> handler) {
        return sendWithRetriesAsync(supplier, handler, 1);
    }

    private <T> CompletableFuture<HttpResponse<T>> sendWithRetriesAsync(Supplier<HttpRequest> supplier,
                                                                        HttpResponse.BodyHandler<T> handler,
                                                                        int attempt) {
        HttpRequest request = supplier.get();
        return client.sendAsync(request, handler)
                .handle((response, error) -> handleAsyncResult(supplier, handler, attempt, response, error))
                .thenCompose(Function.identity());
    }

    private <T> CompletableFuture<HttpResponse<T>> handleAsyncResult(Supplier<HttpRequest> supplier,
                                                                     HttpResponse.BodyHandler<T> handler,
                                                                     int attempt,
                                                                     HttpResponse<T> response,
                                                                     Throwable error) {
        if (error == null) {
            logResponse(response);
            if (!retryPolicy.shouldRetryStatus(response.statusCode()) || attempt >= retryPolicy.maxAttempts()) {
                return CompletableFuture.completedFuture(response);
            }
        } else {
            Throwable cause = unwrap(error);
            if (!retryPolicy.shouldRetryThrowable(cause) || attempt >= retryPolicy.maxAttempts()) {
                CompletableFuture<HttpResponse<T>> failed = new CompletableFuture<>();
                failed.completeExceptionally(cause);
                return failed;
            }
        }
        if (logRetry(attempt, null)) {
            // logged
        }
        long delay = retryPolicy.nextDelayMs(attempt);
        Executor executor = CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS);
        return CompletableFuture.supplyAsync(() -> sendWithRetriesAsync(supplier, handler, attempt + 1), executor)
                .thenCompose(Function.identity());
    }

    private boolean logRetry(int attempt, HttpRequest request) {
        if (!isLoggingEnabled() || !logging.isLogRetries() || logger == null) {
            return false;
        }
        String method = request != null ? request.method() : "request";
        String uri = request != null ? request.uri().toString() : "";
        logger.warn("[HTTP] Retry attempt " + (attempt + 1) + " for " + method + " " + uri);
        return true;
    }

    private void sleep(long delayMs) throws InterruptedException {
        if (delayMs <= 0) {
            return;
        }
        Thread.sleep(delayMs);
    }

    private void logRequest(HttpRequest request, String body) {
        if (!isLoggingEnabled() || logger == null || request == null) {
            return;
        }
        logger.info("[HTTP] " + request.method() + " " + request.uri());
        if (logging.isLogHeaders()) {
            logger.debug("[HTTP] Headers: " + request.headers().map());
        }
        if (logging.isLogBody() && body != null) {
            logger.debug("[HTTP] Body: " + truncate(body));
        }
    }

    private void logResponse(HttpResponse<?> response) {
        if (!isLoggingEnabled() || logger == null || response == null) {
            return;
        }
        logger.info("[HTTP] Response " + response.statusCode() + " " + response.uri());
        if (logging.isLogHeaders()) {
            logger.debug("[HTTP] Headers: " + response.headers().map());
        }
        if (logging.isLogBody() && response.body() instanceof String body) {
            logger.debug("[HTTP] Body: " + truncate(body));
        }
    }

    private boolean isLoggingEnabled() {
        return logging != null && logging.isEnabled();
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        int limit = Math.max(0, logging.getMaxBodyLength());
        if (limit == 0 || body.length() <= limit) {
            return body;
        }
        return body.substring(0, limit) + "...";
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    public static final class Builder {
        private final Platform platform;
        private final ConfigManager configManager;
        private PlatformLogger logger;
        private HttpClientConfig config;
        private HttpClientConfig.LoggingSettings logging;
        private RetryPolicy retryPolicy;
        private ObjectMapper mapper;
        private String baseUrl;
        private String userAgent;
        private Map<String, String> defaultHeaders;
        private Duration connectTimeout;
        private Duration requestTimeout;
        private Boolean followRedirects;
        private HttpClient.Version version;
        private HttpClient client;
        private HttpClient.Builder httpBuilder;

        private Builder(Platform platform, ConfigManager configManager) {
            this.platform = platform;
            this.configManager = configManager;
            this.logger = platform != null ? platform.logger() : null;
            if (configManager != null) {
                this.config = configManager.register(HttpClientConfig.class);
            }
        }

        public Builder logger(PlatformLogger logger) {
            this.logger = logger;
            return this;
        }

        public Builder config(HttpClientConfig config) {
            this.config = config;
            return this;
        }

        public Builder logging(HttpClientConfig.LoggingSettings logging) {
            this.logging = logging;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder mapper(ObjectMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder header(String name, String value) {
            if (defaultHeaders == null) {
                defaultHeaders = new LinkedHashMap<>();
            }
            defaultHeaders.put(name, value);
            return this;
        }

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

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder version(HttpClient.Version version) {
            this.version = version;
            return this;
        }

        public Builder client(HttpClient client) {
            this.client = client;
            return this;
        }

        public Builder httpBuilder(HttpClient.Builder httpBuilder) {
            this.httpBuilder = httpBuilder;
            return this;
        }

        public MagicHttpClient build() {
            return new MagicHttpClient(this);
        }
    }
}
