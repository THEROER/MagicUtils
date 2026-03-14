# Config

MagicUtils config maps files to POJOs using annotations while preserving user
comments and unknown keys when saving.

## Define A Config

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

If your config path contains placeholders such as `{lang}` or `{service}`, pass
them during registration:

```java
ExampleConfig cfg = manager.register(ExampleConfig.class, Map.of("service", "gateway"));
```

## Formats And Selection

- JSON / JSONC are available out of the box.
- YAML requires `magicutils-config-yaml`.
- TOML requires `magicutils-config-toml`.

If you use `{ext}` in `@ConfigFile`, MagicUtils chooses the extension from the
current config format rules:

- `<config>.format` next to the config
- `magicutils.format` in the root config directory
- `-Dmagicutils.config.format=jsonc`
- `MAGICUTILS_CONFIG_FORMAT`

Fabric defaults to JSONC when no explicit format is selected.

For advanced format selection and migrations, see
[Config Advanced](config-advanced.md).

## Reloading And Change Listeners

Register a listener for live updates:

```java
ListenerSubscription subscription = manager.subscribeChanges(ExampleConfig.class, (config, sections) -> {
    // apply live updates
});
```

`onChange(...)` is available as a convenience alias when you do not need the
subscription handle.

`@ConfigReloadable` restricts which sections can reload at runtime.

## Threading Helpers

Reloading touches disk. Use async or smart helpers on blocking-sensitive
threads:

```java
manager.reloadAsync(cfg);
manager.reloadAsync(ExampleConfig.class, "messages");
manager.reloadAllAsync();

manager.reloadSmart(cfg);
manager.reloadSmart(ExampleConfig.class, "messages");
manager.reloadAllSmart();
```

## Runtime-Managed Config Services

When you already have `MagicRuntime`, you can bind a config-backed resource and
let MagicUtils rebuild it automatically on matching config reloads:

```java
MagicRuntimeConfigBinding<ExampleConfig, ReloadableService> binding = runtime.bindConfig(
        "service.example",
        ExampleConfig.class,
        config -> new ReloadableService(config), // ReloadableService implements AutoCloseable
        "messages"
);

ReloadableService service = binding.require();
```

The bound service is also exposed as a named runtime component.

## Migrations

Config migrations are declared with `ConfigMigration` and tracked by the
`config-version` key inside the file:

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

## Validation Annotations

### `@MinValue` / `@MaxValue`

Clamp numeric fields to a safe range. Values outside the range are automatically
adjusted when the config is loaded. A warning is logged by default.

```java
@ConfigValue("retry_interval")
@MinValue(5)
@Comment("Retry interval in seconds (minimum: 5)")
private int retryInterval = 10;

@ConfigValue("max_players")
@MaxValue(100)
@Comment("Maximum players (maximum: 100)")
private int maxPlayers = 20;
```

Supported types: `byte`, `short`, `int`, `long`, `float`, `double` and their
wrapper types.

Set `warn = false` to suppress the clamping log message:

```java
@MinValue(value = 0, warn = false)
```

### `@DefaultValue`

Provides a default string value for a config field when the key is missing from
the file:

```java
@ConfigValue("channel")
@DefaultValue("stable")
private String channel;
```

For dynamic defaults, implement `DefaultValueProvider<T>` and reference it:

```java
@ConfigValue("name")
@DefaultValue(provider = MyDefaultProvider.class)
private String name;
```

## Serializable Types

### `@ConfigSerializable`

Marks a class so it can be used in config lists and maps:

```java
@ConfigSerializable
public class ServerEntry {
    @ConfigValue("name")
    private String name = "";

    @ConfigValue("port")
    private int port = 25565;
}
```

Use `includeNulls = true` to serialize null fields explicitly.

### `@SaveTo`

Redirects a field to a different file:

```java
@ConfigValue("secrets")
@SaveTo("secrets.{ext}")
private Secrets secrets = new Secrets();
```

The path is relative to the plugin data folder.

### `@ListProcessor`

Applies per-item validation or transformation when loading list fields:

```java
@ConfigValue("servers")
@ListProcessor(ServerListProcessor.class)
private List<ServerEntry> servers = new ArrayList<>();
```

The processor implements `ListItemProcessor<T>`:

```java
public class ServerListProcessor implements ListItemProcessor<ServerEntry> {
    @Override
    public ProcessResult<ServerEntry> process(ServerEntry item, int index) {
        if (item.name == null || item.name.isBlank()) {
            return ProcessResult.replaceWithDefault();
        }
        return ProcessResult.ok(item);
    }
}
```

## Custom Value Adapters

Register serializers via `ConfigAdapters.register(...)`:

```java
ConfigAdapters.register(Duration.class, new ConfigValueAdapter<>() {
    public Duration deserialize(Object value) { ... }
    public Object serialize(Duration value) { ... }
});
```

## Shutdown

Bootstrap helpers and `MagicRuntime` can manage the config manager lifecycle
for you. In manual setups, call `ConfigManager.shutdown()` during plugin or mod
shutdown to stop file watchers cleanly.
