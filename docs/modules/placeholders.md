# Placeholders

The placeholder module offers a shared registry and platform bridges to
PlaceholderAPI (Bukkit) and PB4/MiniPlaceholders (Fabric).

## Register placeholders

```java
MagicPlaceholders.registerNamespace("myplugin", "MyPlugin", "1.0.0");

MagicPlaceholders.register("myplugin", "online", (audience, arg) -> "42");
MagicPlaceholders.register("myplugin", "balance", (audience, arg) -> {
    return arg != null ? arg : "0";
});
```

## Resolve placeholders

```java
String value = MagicPlaceholders.resolve("myplugin", "online", audience, null);
```

Resolvers receive:

- `Audience` when available (player context).
- `arg` from `placeholder:key:arg` syntax (PAPI/PB4/MiniPlaceholders).

## Platform bridges

- Bukkit: placeholders are exposed to PlaceholderAPI automatically when the
  logger adapter is created.
- Fabric: placeholders are bridged to PB4 Placeholders and MiniPlaceholders
  when their mods are present.

## Notes

- `MagicPlaceholders.namespaces()` and `entries()` provide registry snapshots.
- `audienceFromUuid(UUID)` creates a lightweight audience wrapper for offline
  resolution.
