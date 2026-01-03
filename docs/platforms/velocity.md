# Velocity

MagicUtils provides a minimal Velocity adapter for config, logger, and lang.
Commands are not wired on Velocity yet.

## Dependency

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-velocity:{{ magicutils_version }}")
}
```

## Platform wiring

Velocity plugins typically receive a data directory via `@DataDirectory`.
Pass that directory to the platform provider so each plugin stays isolated.

```java
@Inject
public MyPlugin(ProxyServer proxy,
                Logger logger,
                @DataDirectory Path dataDir) {
    Platform platform = new VelocityPlatformProvider(proxy, logger, dataDir, this);
    ConfigManager configManager = new ConfigManager(platform);
    Logger muLogger = new Logger(platform, configManager, "MyPlugin");
}
```

## Notes

- `Platform.runOnMain(...)` executes immediately (Velocity has no main thread).
- `ConfigManager` shutdown hooks are registered via `ProxyShutdownEvent` when
  the plugin instance is provided.
