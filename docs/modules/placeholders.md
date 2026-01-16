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

## Render placeholders in text

Use `{placeholder}` tokens to render text with the shared registry:

```java
PlaceholderContext context = PlaceholderContext.builder()
    .audience(audience)
    .defaultNamespace("myplugin")
    .ownerKey(this) // optional local scope key
    .inline(Map.of("player", "Steve"))
    .build();

String text = "Hello {player}! Balance: {balance|bank}";
String rendered = MagicPlaceholders.render(context, text);
```

Resolution order for `{key}`:

1. Inline values from the context.
2. Local placeholders registered for the `ownerKey`.
3. Default namespace (`defaultNamespace:key`).
4. Global placeholders.

Namespaced tokens use `{namespace:key}` or `{namespace:key:arg}`. Unqualified
tokens with `:` are not supported. Use the argument separator (default `|`)
for `key|arg` instead.

To change or disable the separator:

```java
PlaceholderContext context = PlaceholderContext.builder()
    .argumentSeparator("::") // set your own
    .build();
```

Convenience overloads:

```java
String rendered = MagicPlaceholders.render(audience, "Hello {server}");
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
