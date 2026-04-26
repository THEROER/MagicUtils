package dev.ua.theroer.magicutils.http;

import org.junit.jupiter.api.Test;

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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicWebSocketClientTest {

    @Test
    void resolvesRelativePathsAgainstBaseUrlWithoutTrailingSlash() {
        TestHttpClient httpClient = new TestHttpClient();
        MagicWebSocketClient client = MagicWebSocketClient.builder(null)
                .baseUrl("https://example.com/api")
                .client(httpClient)
                .build();
        try {
            client.connectAsync("socket", new WebSocket.Listener() {
            }).join();
            assertEquals(URI.create("wss://example.com/api/socket"), httpClient.webSocketBuilder.uri);
        } finally {
            client.close();
        }
    }

    @Test
    void keepsAbsoluteWebSocketUrlsUnchanged() {
        TestHttpClient httpClient = new TestHttpClient();
        MagicWebSocketClient client = MagicWebSocketClient.builder(null)
                .baseUrl("https://example.com/api")
                .client(httpClient)
                .build();
        try {
            client.connectAsync("ws://other.example.net/live", new WebSocket.Listener() {
            }).join();
            assertEquals(URI.create("ws://other.example.net/live"), httpClient.webSocketBuilder.uri);
        } finally {
            client.close();
        }
    }

    @Test
    void doesNotCloseExternallyProvidedClient() {
        TestHttpClient external = new TestHttpClient();
        MagicWebSocketClient client = MagicWebSocketClient.builder(null)
                .client(external)
                .build();

        client.close();

        assertFalse(external.closed.get());
    }

    @Test
    void closesInternallyCreatedClientFromCustomBuilder() {
        TestHttpClient owned = new TestHttpClient();
        MagicWebSocketClient client = MagicWebSocketClient.builder(null)
                .httpBuilder(new TestHttpClientBuilder(owned))
                .build();

        client.close();

        assertTrue(owned.closed.get());
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

        public void close() {
            closed.set(true);
        }
    }

    private static final class RecordingWebSocketBuilder implements WebSocket.Builder {
        private URI uri;

        @Override
        public WebSocket.Builder header(String name, String value) {
            return this;
        }

        @Override
        public WebSocket.Builder connectTimeout(Duration timeout) {
            return this;
        }

        @Override
        public WebSocket.Builder subprotocols(String mostPreferred, String... lesserPreferred) {
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
