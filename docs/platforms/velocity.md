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

## Notes

- `Platform.runOnMain(...)` executes immediately because Velocity has no main
  game thread.
- `buildRuntime()` returns a managed `MagicRuntime` and wires shutdown cleanup
  through the plugin instance you pass to the platform provider.
