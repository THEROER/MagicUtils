# Config

MagicUtils config maps files to POJOs using annotations while preserving user
comments and unknown keys when saving.

## Define a config

```java
@ConfigFile("example.{ext}")
@Comment("Example configuration")
@ConfigReloadable(sections = {"messages"})
public final class ExampleConfig {
    @ConfigValue("enabled")
    private boolean enabled = true;

    @ConfigSection("messages")
    private Messages messages = new Messages();

    public static final class Messages {
        @ConfigValue("greeting")
        @Comment("Greeting shown to players")
        private String greeting = "Hello";
    }
}
```

```java
ConfigManager manager = new ConfigManager(platform);
ExampleConfig cfg = manager.register(ExampleConfig.class);
```

## Formats and selection

- JSON/JSONC are available out of the box.
- YAML requires `magicutils-config-yaml`.
- TOML requires `magicutils-config-toml`.

MagicUtils can choose a format based on file extension. If you use `{ext}` in
`@ConfigFile`, it will follow the preferred format:

- `example.format` next to the config (contents: `json`, `jsonc`, `yml`, `toml`).
- `magicutils.format` in the root config directory (global default).
- `-Dmagicutils.config.format=jsonc` or the `MAGICUTILS_CONFIG_FORMAT` env var.

Fabric defaults to JSONC when YAML is not available.

## Reloading and hot watch

Register change listeners:

```java
manager.onChange(ExampleConfig.class, (config, sections) -> {
    // apply live updates
});
```

`@ConfigReloadable` restricts which sections may reload at runtime.

## Migrations

Config migrations are declared with `ConfigMigration` and tracked by the
`config-version` key inside the file.

```java
manager.registerMigrations(ExampleConfig.class,
        new ConfigMigration() {
            public String fromVersion() { return "0"; }
            public String toVersion() { return "1"; }
            public void migrate(Map<String, Object> root) {
                root.put("enabled", true);
            }
        }
);
```

## Custom value adapters

Register custom serializers via `ConfigAdapters.register(...)`:

```java
ConfigAdapters.register(Duration.class, new ConfigValueAdapter<>() {
    public Duration deserialize(Object value) { ... }
    public Object serialize(Duration value) { ... }
});
```

## Shutdown

On Bukkit and Fabric, MagicUtils automatically stops the watcher when the
server shuts down. For custom platforms, call `ConfigManager.shutdown()` during
plugin shutdown.
