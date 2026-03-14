# Velocity

`magicutils-velocity` exposes platform wiring, config, logger, lang, and
command integration in one artifact.

## Dependency

```kotlin
dependencies {
    implementation("dev.ua.theroer:magicutils-velocity:{{ magicutils_version }}")
}
```

## Recommended Bootstrap

```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public final class MyPlugin {
    private final ProxyServer proxy;
    private final org.slf4j.Logger slf4j;
    private final Path dataDirectory;
    private VelocityBootstrap.RuntimeResult magic;

    @Inject
    public MyPlugin(ProxyServer proxy,
                    org.slf4j.Logger slf4j,
                    @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.slf4j = slf4j;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        magic = VelocityBootstrap.forPlugin(proxy, this, "MyPlugin", dataDirectory)
                .slf4j(slf4j)
                .enableCommands()
                .configureCommands(registry -> registry.registerCommand(new ExampleCommand()))
                .buildRuntime();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (magic != null) {
            magic.runtime().close();
            magic = null;
        }
    }
}
```

`VelocityBootstrap` creates the `Platform`, `ConfigManager`, `LoggerCore`,
`LanguageManager`, and optional `CommandRegistry` for the plugin.

## Commands

Velocity commands register directly with the proxy command manager.

- Use `enableCommands()` to create the registry during bootstrap.
- Use `configureCommands(...)` to register commands right away.
- Use `asyncExecutor(...)` when you want to override the default async executor
  used by the command layer.

## Bootstrap Options

`VelocityBootstrap.Builder` supports additional configuration beyond the basics:

| Method | Description |
| --- | --- |
| `slf4j(logger)` | Bind SLF4J logger for console output. |
| `enableCommands()` | Create a `CommandRegistry` during bootstrap. |
| `permissionPrefix(prefix)` | Set the permission node prefix for commands. |
| `asyncExecutor(executor)` | Override the async executor used by the command layer. |
| `configureCommands(consumer)` | Register commands during bootstrap. |
| `initLanguage(boolean)` | Enable/disable language manager initialization. |
| `bindLoggerLanguage(boolean)` | Bind the language manager to the logger. |
| `setMessagesManager(boolean)` | Set the global `Messages` language manager. |
| `registerMessages(boolean)` | Register the plugin's messages scope. |
| `addMagicUtilsMessages(boolean)` | Register built-in MagicUtils messages. |
| `translations(consumer)` | Configure additional translations. |

## Player Events

Velocity supports the platform-agnostic player lifecycle and message events:

```java
platform.subscribePlayerLifecycle(event -> {
    if (event.type() == PlayerLifecycleType.JOIN) {
        logger.info(event.playerName() + " connected");
    }
});
```

See [Core / Common Logic](core.md#player-events) for the full event API.

## Notes

- `Platform.runOnMain(...)` executes immediately because Velocity has no main
  game thread.
- `buildRuntime()` returns a managed `MagicRuntime` and wires shutdown cleanup
  through the plugin instance you pass to the platform provider.
