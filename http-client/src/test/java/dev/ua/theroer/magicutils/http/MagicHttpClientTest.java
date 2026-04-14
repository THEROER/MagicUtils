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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicHttpClientTest {

    @Test
    void resolvesRelativePathsAgainstBaseUrlWithoutTrailingSlash() {
        MagicHttpClient client = MagicHttpClient.builder(null)
                .baseUrl("https://example.com/api")
                .build();
        try {
            HttpRequest request = client.request("users").build();
            assertEquals(URI.create("https://example.com/api/users"), request.uri());
        } finally {
            client.close();
        }
    }

    @Test
    void keepsAbsoluteRequestUrlsUnchanged() {
        MagicHttpClient client = MagicHttpClient.builder(null)
                .baseUrl("https://example.com/api")
                .build();
        try {
            HttpRequest request = client.request("https://other.example.net/ping").build();
            assertEquals(URI.create("https://other.example.net/ping"), request.uri());
        } finally {
            client.close();
        }
    }

    @Test
    void doesNotCloseExternallyProvidedClient() {
        TestHttpClient external = new TestHttpClient();
        MagicHttpClient client = MagicHttpClient.builder(null)
                .client(external)
                .build();

        client.close();

        assertFalse(external.closed.get());
    }

    @Test
    void closesInternallyCreatedClientFromCustomBuilder() {
        TestHttpClient owned = new TestHttpClient();
        MagicHttpClient client = MagicHttpClient.builder(null)
                .httpBuilder(new TestHttpClientBuilder(owned))
                .build();

        client.close();

        assertTrue(owned.closed.get());
    }

    private static final class TestHttpClient extends HttpClient {
        private final AtomicBoolean closed = new AtomicBoolean(false);

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
