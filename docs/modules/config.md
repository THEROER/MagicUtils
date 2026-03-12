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
