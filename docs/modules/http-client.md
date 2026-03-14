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

## WebSocket client

`MagicWebSocketClient` wraps `java.net.http.WebSocket` with the same builder
pattern and config integration as the HTTP client.

### Basic usage

```java
MagicWebSocketClient wsClient = MagicWebSocketClient.builder(platform, configManager)
        .baseUrl("wss://api.example.com/ws")
        .header("Authorization", "Bearer token")
        .subprotocols(List.of("v1"))
        .build();

CompletableFuture<WebSocket> ws = wsClient.connectAsync("/events", myListener);
```

### Builder options

The builder supports the same options as `MagicHttpClient.Builder`:

- `baseUrl(...)` — base URL prepended to connect paths
- `header(...)` / `headers(...)` — default headers
- `userAgent(...)` — User-Agent header
- `connectTimeout(...)` — connection timeout
- `followRedirects(...)` — redirect policy
- `subprotocols(...)` — WebSocket subprotocol list
- `logger(...)` — platform logger for connection diagnostics
- `config(...)` / `logging(...)` — shared `HttpClientConfig` settings
- `mapper(...)` — custom Jackson `ObjectMapper`

### Connect methods

| Method | Description |
| --- | --- |
| `connect(path, listener)` | Synchronous connect (blocks). |
| `connectAsync(path, listener)` | Returns `CompletableFuture<WebSocket>`. |
| `connectSmart(path, listener)` | Async on blocking-sensitive threads, sync otherwise. |

### Runtime profiles

Use `MagicWebSocketClientProfile` for config-aware WebSocket clients that
rebuild on config reload:

```java
MagicWebSocketClientProfile<ApiConfig> gateway = MagicWebSocketClientProfile
        .builder(runtime, "ws.gateway", ApiConfig.class)
        .sections("gateway")
        .baseUrl(config -> config.gateway.wsUrl)
        .bearerAuth(config -> config.gateway.token)
        .subprotocols(config -> config.gateway.subprotocols)
        .build();

MagicWebSocketClient client = gateway.require();
```

### Cleanup

Call `wsClient.close()` to release resources. When using runtime profiles, the
profile handles cleanup automatically on config reload and runtime shutdown.

## Async method variants

Every convenience method on `MagicHttpClient` has three variants:

| Suffix | Behaviour |
| --- | --- |
| _(none)_ | Synchronous. Throws on blocking-sensitive threads. |
| `...Async(...)` | Returns `CompletableFuture`. Always non-blocking. |
| `...Smart(...)` | Sync when safe, async when on a blocking-sensitive thread. |

Available methods: `get`, `getJson`, `post`, `postJson`, `postMultipart`,
`send`.

## Notes

- Retries apply to the convenience methods (`get`, `post`, `postJson`, etc.).
- For raw `send(...)`, retries are not automatic.
- JSON mapping uses Jackson; invalid JSON throws `IllegalStateException`.
- Synchronous methods throw `IllegalStateException` on blocking-sensitive
  threads. Use `...Async()` or `...Smart()` in handlers.
