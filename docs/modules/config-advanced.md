# Config advanced

This page covers advanced configuration features: format selection, migrations,
and adapters.

## Format selection

If your `@ConfigFile` uses `{ext}`, MagicUtils can switch formats using:

- `<config>.format` next to the config file (one line: `json`, `jsonc`, `yml`, `toml`)
- `magicutils.format` in the config root directory
- `-Dmagicutils.config.format=...`
- `MAGICUTILS_CONFIG_FORMAT` environment variable

If multiple formats exist, MagicUtils picks one and logs a warning.

## Format migration

When `<config>.format` changes and an older format file exists, MagicUtils can
migrate the contents into the new format and keep both in sync.

## Schema migrations

Register `ConfigMigration` steps to evolve config schemas.

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

MagicUtils stores the current schema in `config-version`.

## Custom adapters

Use `ConfigAdapters.register(...)` for custom value types:

```java
ConfigAdapters.register(Duration.class, new ConfigValueAdapter<>() {
    public Duration deserialize(Object value) { ... }
    public Object serialize(Duration value) { ... }
});
```

## Hot reload

Mark reloadable sections and listen for updates:

```java
@ConfigReloadable(sections = {"messages"})
public final class MyConfig { ... }

manager.onChange(MyConfig.class, (cfg, sections) -> {
    // apply live update
});
```
