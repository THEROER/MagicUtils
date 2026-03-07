package dev.ua.theroer.magicutils.http;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntimeConfigBinding;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicRuntimeHttpBindingTest {
    @TempDir
    Path tempDir;

    @Test
    void httpClientBindingReloadsMatchingSectionAndClosesReplacedClient() throws Exception {
        try (TestContext context = newContext("http-binding")) {
            List<TestHttpClient> ownedClients = new ArrayList<>();
            MagicRuntime runtime = newRuntime(context);
            MagicRuntimeConfigBinding<EndpointConfig, MagicHttpClient> binding = runtime.bindConfig(
                    "http.api",
                    EndpointConfig.class,
                    config -> {
                        TestHttpClient owned = new TestHttpClient();
                        ownedClients.add(owned);
                        return MagicHttpClient.builder(context.platform())
                                .baseUrl(config.http.baseUrl)
                                .httpBuilder(new TestHttpClientBuilder(owned))
                                .build();
                    },
                    "http"
            );
            context.configManager().shutdown();

            MagicHttpClient initial = binding.require();
            assertEquals(URI.create("https://one.example/api/users"), initial.request("users").build().uri());
            assertSame(initial, runtime.requireNamedComponent("http.api", MagicHttpClient.class));

            Files.writeString(tempDir.resolve("http-binding/endpoints.json"),
                    "{\n" +
                            "  \"http\" : { \"baseUrl\" : \"https://two.example/api\" },\n" +
                            "  \"socket\" : { \"baseUrl\" : \"https://socket.example/live\" }\n" +
                            "}\n");

            context.configManager().reload(EndpointConfig.class, "socket");
            assertSame(initial, binding.require());

            context.configManager().reload(EndpointConfig.class, "http");

            MagicHttpClient updated = binding.require();
            assertNotSame(initial, updated);
            assertEquals(URI.create("https://two.example/api/users"), updated.request("users").build().uri());
            assertTrue(ownedClients.get(0).closed.get());
            assertSame(updated, runtime.requireNamedComponent("http.api", MagicHttpClient.class));

            runtime.close();

            assertTrue(ownedClients.get(1).closed.get());
        }
    }

    @Test
    void webSocketBindingReloadsMatchingSectionAndUpdatesNamedComponent() throws Exception {
        try (TestContext context = newContext("websocket-binding")) {
            List<TestHttpClient> ownedClients = new ArrayList<>();
            MagicRuntime runtime = newRuntime(context);
            MagicRuntimeConfigBinding<EndpointConfig, MagicWebSocketClient> binding = runtime.bindConfig(
                    "ws.gateway",
                    EndpointConfig.class,
                    config -> {
                        TestHttpClient owned = new TestHttpClient();
                        ownedClients.add(owned);
                        return MagicWebSocketClient.builder(context.platform())
                                .baseUrl(config.socket.baseUrl)
                                .httpBuilder(new TestHttpClientBuilder(owned))
                                .build();
                    },
                    "socket"
            );
            context.configManager().shutdown();

            MagicWebSocketClient initial = binding.require();
            initial.connectAsync("events", new WebSocket.Listener() {
            }).join();
            assertEquals(URI.create("wss://one.example/live/events"), ownedClients.get(0).webSocketBuilder.uri);
            assertSame(initial, runtime.requireNamedComponent("ws.gateway", MagicWebSocketClient.class));

            Files.writeString(tempDir.resolve("websocket-binding/endpoints.json"),
                    "{\n" +
                            "  \"http\" : { \"baseUrl\" : \"https://unchanged.example/api\" },\n" +
                            "  \"socket\" : { \"baseUrl\" : \"https://two.example/live\" }\n" +
                            "}\n");

            context.configManager().reload(EndpointConfig.class, "http");
            assertSame(initial, binding.require());

            context.configManager().reload(EndpointConfig.class, "socket");

            MagicWebSocketClient updated = binding.require();
            assertNotSame(initial, updated);
            updated.connectAsync("events", new WebSocket.Listener() {
            }).join();
            assertEquals(URI.create("wss://two.example/live/events"), ownedClients.get(1).webSocketBuilder.uri);
            assertTrue(ownedClients.get(0).closed.get());
            assertSame(updated, runtime.requireNamedComponent("ws.gateway", MagicWebSocketClient.class));

            binding.close();

            assertTrue(ownedClients.get(1).closed.get());
        }
    }

    @Test
    void httpProfileBuildsNamedClientFromConfigAndRebuildsOnSectionReload() throws Exception {
        try (TestContext context = newContext("http-profile")) {
            List<TestHttpClient> ownedClients = new ArrayList<>();
            MagicRuntime runtime = newRuntime(context);
            MagicHttpClientProfile<HttpProfileConfig> profile = MagicHttpClientProfile
                    .builder(runtime, "http.monitoring", HttpProfileConfig.class)
                    .sections("monitoring")
                    .baseUrl(config -> config.monitoring.baseUrl)
                    .bearerAuth(config -> config.monitoring.token)
                    .header("X-Scope", config -> config.monitoring.scope)
                    .configure((config, builder) -> {
                        TestHttpClient owned = new TestHttpClient();
                        ownedClients.add(owned);
                        builder.httpBuilder(new TestHttpClientBuilder(owned));
                    })
                    .build();
            context.configManager().shutdown();

            MagicHttpClient initial = profile.require();
            HttpRequest initialRequest = initial.request("status").build();
            assertEquals(URI.create("https://monitoring.example/api/status"), initialRequest.uri());
            assertEquals("Bearer token-one",
                    initialRequest.headers().firstValue("Authorization").orElseThrow());
            assertEquals("monitoring", initialRequest.headers().firstValue("X-Scope").orElseThrow());
            assertSame(initial, runtime.requireNamedComponent("http.monitoring", MagicHttpClient.class));

            Files.writeString(tempDir.resolve("http-profile/http-profile.json"),
                    "{\n" +
                            "  \"monitoring\" : {\n" +
                            "    \"baseUrl\" : \"https://monitoring-two.example/api\",\n" +
                            "    \"token\" : \"token-two\",\n" +
                            "    \"scope\" : \"updated\"\n" +
                            "  }\n" +
                            "}\n");

            context.configManager().reload(HttpProfileConfig.class, "monitoring");

            MagicHttpClient updated = profile.require();
            assertNotSame(initial, updated);
            HttpRequest updatedRequest = updated.request("status").build();
            assertEquals(URI.create("https://monitoring-two.example/api/status"), updatedRequest.uri());
            assertEquals("Bearer token-two",
                    updatedRequest.headers().firstValue("Authorization").orElseThrow());
            assertEquals("updated", updatedRequest.headers().firstValue("X-Scope").orElseThrow());
            assertTrue(ownedClients.get(0).closed.get());
            assertFalse(ownedClients.get(1).closed.get());

            profile.close();

            assertTrue(ownedClients.get(1).closed.get());
            assertTrue(runtime.findNamedComponent("http.monitoring", MagicHttpClient.class).isEmpty());
        }
    }

    @Test
    void webSocketProfileBuildsNamedClientFromConfigAndRebuildsOnSectionReload() throws Exception {
        try (TestContext context = newContext("websocket-profile")) {
            List<TestHttpClient> ownedClients = new ArrayList<>();
            MagicRuntime runtime = newRuntime(context);
            MagicWebSocketClientProfile<WebSocketProfileConfig> profile = MagicWebSocketClientProfile
                    .builder(runtime, "ws.gateway.profile", WebSocketProfileConfig.class)
                    .sections("gateway")
                    .baseUrl(config -> config.gateway.baseUrl)
                    .bearerAuth(config -> config.gateway.token)
                    .subprotocols(config -> config.gateway.subprotocols)
                    .configure((config, builder) -> {
                        TestHttpClient owned = new TestHttpClient();
                        ownedClients.add(owned);
                        builder.httpBuilder(new TestHttpClientBuilder(owned));
                    })
                    .build();
            context.configManager().shutdown();

            MagicWebSocketClient initial = profile.require();
            initial.connectAsync("events", new WebSocket.Listener() {
            }).join();
            assertEquals(URI.create("wss://gateway.example/live/events"), ownedClients.get(0).webSocketBuilder.uri);
            assertEquals("Bearer gateway-token",
                    ownedClients.get(0).webSocketBuilder.headers.get("Authorization"));
            assertEquals(List.of("verified.v1", "verified.v2"), ownedClients.get(0).webSocketBuilder.subprotocols);
            assertSame(initial, runtime.requireNamedComponent("ws.gateway.profile", MagicWebSocketClient.class));

            Files.writeString(tempDir.resolve("websocket-profile/ws-profile.json"),
                    "{\n" +
                            "  \"gateway\" : {\n" +
                            "    \"baseUrl\" : \"https://gateway-two.example/live\",\n" +
                            "    \"token\" : \"gateway-two\",\n" +
                            "    \"subprotocols\" : [\"verified.v3\"]\n" +
                            "  }\n" +
                            "}\n");

            context.configManager().reload(WebSocketProfileConfig.class, "gateway");

            MagicWebSocketClient updated = profile.require();
            assertNotSame(initial, updated);
            updated.connectAsync("events", new WebSocket.Listener() {
            }).join();
            assertEquals(URI.create("wss://gateway-two.example/live/events"), ownedClients.get(1).webSocketBuilder.uri);
            assertEquals("Bearer gateway-two",
                    ownedClients.get(1).webSocketBuilder.headers.get("Authorization"));
            assertEquals(List.of("verified.v3"), ownedClients.get(1).webSocketBuilder.subprotocols);
            assertTrue(ownedClients.get(0).closed.get());

            runtime.close();

            assertTrue(ownedClients.get(1).closed.get());
        }
    }

    private MagicRuntime newRuntime(TestContext context) {
        return MagicRuntime.builder(context.platform(), context.configManager(), context.logger())
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build();
    }

    private TestContext newContext(String name) throws IOException {
        Path directory = Files.createDirectories(tempDir.resolve(name));
        TestPlatform platform = new TestPlatform(directory);
        ConfigManager configManager = new ConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "MagicRuntimeHttpBindingTest");
        return new TestContext(platform, configManager, logger);
    }

    @ConfigFile("endpoints.json")
    @ConfigReloadable(sections = { "http", "socket" })
    public static final class EndpointConfig {
        @ConfigSection("http")
        HttpEndpoint http = new HttpEndpoint();

        @ConfigSection("socket")
        SocketEndpoint socket = new SocketEndpoint();
    }

    public static final class HttpEndpoint {
        @ConfigValue("baseUrl")
        String baseUrl = "https://one.example/api";
    }

    public static final class SocketEndpoint {
        @ConfigValue("baseUrl")
        String baseUrl = "https://one.example/live";
    }

    @ConfigFile("http-profile.json")
    @ConfigReloadable(sections = { "monitoring" })
    public static final class HttpProfileConfig {
        @ConfigSection("monitoring")
        MonitoringEndpoint monitoring = new MonitoringEndpoint();
    }

    public static final class MonitoringEndpoint {
        @ConfigValue("baseUrl")
        String baseUrl = "https://monitoring.example/api";

        @ConfigValue("token")
        String token = "token-one";

        @ConfigValue("scope")
        String scope = "monitoring";
    }

    @ConfigFile("ws-profile.json")
    @ConfigReloadable(sections = { "gateway" })
    public static final class WebSocketProfileConfig {
        @ConfigSection("gateway")
        GatewayEndpoint gateway = new GatewayEndpoint();
    }

    public static final class GatewayEndpoint {
        @ConfigValue("baseUrl")
        String baseUrl = "https://gateway.example/live";

        @ConfigValue("token")
        String token = "gateway-token";

        @ConfigValue("subprotocols")
        List<String> subprotocols = new ArrayList<>(List.of("verified.v1", "verified.v2"));
    }

    private record TestContext(TestPlatform platform,
                               ConfigManager configManager,
                               LoggerCore logger) implements AutoCloseable {
        @Override
        public void close() {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static final class TestPlatform implements Platform {
        private final Path configDir;
        private final TaskScheduler scheduler = new DirectTaskScheduler();

        private TestPlatform(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public Path configDir() {
            return configDir;
        }

        @Override
        public PlatformLogger logger() {
            return NoOpLogger.INSTANCE;
        }

        @Override
        public Audience console() {
            return null;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return Collections.emptyList();
        }

        @Override
        public void runOnMain(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public boolean isMainThread() {
            return false;
        }

        @Override
        public TaskScheduler scheduler() {
            return scheduler;
        }

        private void shutdown() {
            scheduler.shutdown();
        }
    }

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "runtime-http-binding-test");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public Executor cpu() {
            return directExecutor;
        }

        @Override
        public Executor io() {
            return directExecutor;
        }

        @Override
        public ScheduledExecutorService scheduler() {
            return timer;
        }

        @Override
        public void shutdown() {
            timer.shutdownNow();
        }
    }

    private enum NoOpLogger implements PlatformLogger {
        INSTANCE;

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }

    private static final class TestHttpClient extends HttpClient {
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final RecordingWebSocketBuilder webSocketBuilder = new RecordingWebSocketBuilder();

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return webSocketBuilder;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class RecordingWebSocketBuilder implements WebSocket.Builder {
        private URI uri;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private List<String> subprotocols = List.of();

        @Override
        public WebSocket.Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        @Override
        public WebSocket.Builder connectTimeout(Duration timeout) {
            return this;
        }

        @Override
        public WebSocket.Builder subprotocols(String mostPreferred, String... lesserPreferred) {
            List<String> values = new ArrayList<>();
            values.add(mostPreferred);
            Collections.addAll(values, lesserPreferred);
            subprotocols = List.copyOf(values);
            return this;
        }

        @Override
        public CompletableFuture<WebSocket> buildAsync(URI uri, WebSocket.Listener listener) {
            this.uri = uri;
            return CompletableFuture.completedFuture(new TestWebSocket());
        }
    }

    private static final class TestWebSocket implements WebSocket {
        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(java.nio.ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(java.nio.ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(java.nio.ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
        }
    }

    private static final class TestHttpClientBuilder implements HttpClient.Builder {
        private final HttpClient client;

        private TestHttpClientBuilder(HttpClient client) {
            this.client = client;
        }

        @Override
        public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
            return this;
        }

        @Override
        public HttpClient.Builder connectTimeout(Duration duration) {
            return this;
        }

        @Override
        public HttpClient.Builder sslContext(SSLContext sslContext) {
            return this;
        }

        @Override
        public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
            return this;
        }

        @Override
        public HttpClient.Builder executor(Executor executor) {
            return this;
        }

        @Override
        public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
            return this;
        }

        @Override
        public HttpClient.Builder version(HttpClient.Version version) {
            return this;
        }

        @Override
        public HttpClient.Builder priority(int priority) {
            return this;
        }

        @Override
        public HttpClient.Builder proxy(ProxySelector proxySelector) {
            return this;
        }

        @Override
        public HttpClient.Builder authenticator(Authenticator authenticator) {
            return this;
        }

        @Override
        public HttpClient build() {
            return client;
        }
    }
}
