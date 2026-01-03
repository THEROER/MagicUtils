# Config

MagicUtils config maps files to POJOs with annotations and keeps user edits
intact when saving.

## Example

```java
@ConfigFile("example.yml")
public final class ExampleConfig {
    @ConfigValue("enabled")
    private boolean enabled = true;

    @ConfigSection("messages")
    private Messages messages = new Messages();

    public static final class Messages {
        @ConfigValue("greeting")
        private String greeting = "Hello";
    }
}
```

```java
ConfigManager manager = new ConfigManager(new BukkitPlatformProvider(plugin));
ExampleConfig cfg = manager.register(ExampleConfig.class);
```

## Formats

- JSON/JSONC are supported out of the box.
- YAML requires `magicutils-config-yaml`.
- TOML requires `magicutils-config-toml`.

Fabric defaults to JSONC when YAML is unavailable.

## Shutdown

On Bukkit and Fabric, MagicUtils automatically stops the watcher when the
server shuts down. If you run a custom platform adapter, call
`ConfigManager.shutdown()` during your plugin shutdown.
