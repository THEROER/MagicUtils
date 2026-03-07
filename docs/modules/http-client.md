# HTTP client

MagicUtils HTTP client wraps `java.net.http.HttpClient` with config defaults,
JSON mapping, retries, and a small multipart helper.

## Dependency

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-http-client:{{ magicutils_version }}")
}
```

## Configuration

The module stores settings in `http-client.{ext}` (JSONC by default on Fabric).

Key sections:

- `timeouts`: connect + request timeouts
- `retry`: backoff settings and retry status codes
- `logging`: request/response logging toggles
- `defaults`: base URL, headers, HTTP version

## Basic usage

```java
Platform platform = new BukkitPlatformProvider(this);
ConfigManager configManager = new ConfigManager(platform);

MagicHttpClient client = MagicHttpClient.builder(platform, configManager)
        .baseUrl("https://api.example.com/")
        .header("Authorization", "Bearer token")
        .build();

HttpResponse<String> response = client.get("status");
```

## Runtime profiles

When you already have a `MagicRuntime`, you can declare named HTTP/WebSocket
profiles that rebuild automatically on config reload:

```java
MagicHttpClientProfile<ApiConfig> monitoring = MagicHttpClientProfile
        .builder(runtime, "http.monitoring", ApiConfig.class)
        .sections("monitoring")
        .baseUrl(config -> config.monitoring.baseUrl)
        .bearerAuth(config -> config.monitoring.token)
        .build();

MagicHttpClient client = monitoring.require();
MagicHttpClient sameClient = runtime.requireNamedComponent("http.monitoring", MagicHttpClient.class);
```

```java
MagicWebSocketClientProfile<ApiConfig> gateway = MagicWebSocketClientProfile
        .builder(runtime, "ws.gateway", ApiConfig.class)
        .sections("gateway")
        .baseUrl(config -> config.gateway.baseUrl)
        .bearerAuth(config -> config.gateway.token)
        .subprotocols(config -> config.gateway.subprotocols)
        .build();
```

This removes the usual `close old client -> build new client -> swap references`
boilerplate from service reload paths.

## Smart methods

Smart methods switch to async automatically on blocking-sensitive threads and
return a `CompletableFuture`:

```java
client.getSmart("status").thenAccept(response -> {
    // ...
});
```

## JSON to POJO

```java
public record StatusResponse(String status) {}

StatusResponse response = client.getJson("status", StatusResponse.class);
```

## JSON POST

```java
public record CreateRequest(String name) {}

client.postJson("items", new CreateRequest("Test"));
```

## Multipart upload

```java
MultipartBody body = MultipartBody.builder()
        .text("title", "Hello")
        .file("file", Path.of("logo.png"))
        .build();

client.postMultipart("upload", body);
```

## Notes

- Retries apply to the convenience methods (`get`, `post`, `postJson`, etc.).
- For raw `send(...)`, retries are not automatic.
- JSON mapping uses Jackson; invalid JSON throws `IllegalStateException`.
- Synchronous methods throw `IllegalStateException` on blocking-sensitive
  threads. Use `...Async()` or `...Smart()` in handlers.
