# Placeholders

The placeholder module offers a shared registry and platform bridges to
[PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) (Bukkit) and
[Text Placeholder API](https://modrinth.com/mod/placeholder-api) /
[MiniPlaceholders](https://modrinth.com/mod/miniplaceholders) (Fabric).

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
- `arg` from `placeholder:key:arg` syntax (used by
  [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi),
  [Text Placeholder API](https://modrinth.com/mod/placeholder-api),
  [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)).

## Platform bridges

- Bukkit: placeholders are exposed to [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) automatically when the
  logger adapter is created.
- Fabric: placeholders are bridged to [Text Placeholder API](https://modrinth.com/mod/placeholder-api) and
  [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)
  when those mods are present (optional integration).

Supported Fabric mods:

- [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
- [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)

See [Placeholders Advanced](placeholders-advanced.md) for metadata and listener
details.

## Notes

- `MagicPlaceholders.namespaces()` and `entries()` provide registry snapshots.
- `audienceFromUuid(UUID)` creates a lightweight audience wrapper for offline
  resolution.
