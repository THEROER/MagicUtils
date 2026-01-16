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
- Synchronous methods throw `IllegalStateException` when called from the main
  thread (use the `...Async()` variants in handlers).
