# Config Advanced

This page covers format selection, migrations, adapters, and runtime-aware
reload patterns.

## Format Selection

If your `@ConfigFile` uses `{ext}`, MagicUtils can switch formats using:

- `<config>.format` next to the config file
- `magicutils.format` in the config root directory
- `-Dmagicutils.config.format=...`
- `MAGICUTILS_CONFIG_FORMAT`

Supported values include `json`, `jsonc`, `yml`, `yaml`, and `toml` depending
on the installed format helpers.

If multiple candidate files exist, MagicUtils picks one and logs a warning.

## Format Migration

When the selected format changes and an older format file exists, MagicUtils
can migrate the data into the new target file. This is useful when moving from
JSONC to YAML or TOML without forcing users to recreate their configs.

## Schema Migrations

Register ordered `ConfigMigration` steps to evolve config schemas:

```java
manager.registerMigrations(MyConfig.class,
        new ConfigMigration() {
            public String fromVersion() { return "0"; }
            public String toVersion() { return "1"; }
            public void migrate(Map<String, Object> root) {
                root.put("enabled", true);
            }
        }
);
```

MagicUtils stores the current schema version in `config-version`.

## Custom Adapters

Use `ConfigAdapters.register(...)` for custom value types:

```java
ConfigAdapters.register(Duration.class, new ConfigValueAdapter<>() {
    public Duration deserialize(Object value) { ... }
    public Object serialize(Duration value) { ... }
});
```

Register adapters before the affected config class is first loaded.

## Change Subscriptions

Use `subscribeChanges(...)` when you want an unsubscribe handle:

```java
ListenerSubscription subscription = manager.subscribeChanges(MyConfig.class, (cfg, sections) -> {
    // apply live update
});
```

Use `onChange(...)` when you only need fire-and-forget registration.

## Runtime Resource Binding

`MagicRuntime` can rebuild managed resources on matching config changes:

```java
MagicRuntimeConfigBinding<ServiceConfig, ReloadableClient> binding = runtime.bindConfig(
        "service.client",
        ServiceConfig.class,
        config -> new ReloadableClient(config), // ReloadableClient implements AutoCloseable
        "service"
);
```

Useful properties of this pattern:

- the latest resource is accessible via `binding.require()`
- the same resource is exposed through
  `runtime.requireNamedComponent("service.client", ReloadableClient.class)`
- replaced resources are closed automatically
- `binding.close()` removes the named runtime component and stops listening for
  config changes

## Hot Reload

Mark reloadable sections and listen for updates:

```java
@ConfigReloadable(sections = {"messages"})
public final class MyConfig { ... }
```

Use section-aware reloads to avoid rebuilding unrelated services:

```java
manager.reload(MyConfig.class, "messages");
manager.reloadAsync(MyConfig.class, "messages");
manager.reloadSmart(MyConfig.class, "messages");
```
