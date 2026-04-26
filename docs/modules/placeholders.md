# Placeholders

The placeholder module offers a shared registry plus platform bridges for:

- Bukkit PlaceholderAPI
- Fabric PB4 Placeholder API
- Fabric Text Placeholder API
- Fabric MiniPlaceholders

## Register Placeholders

```java
MagicPlaceholders.registerNamespace("myplugin", "MyPlugin", "1.0.0");

MagicPlaceholders.register("myplugin", "online", (audience, arg) -> "42");
MagicPlaceholders.register("myplugin", "balance", (audience, arg) -> {
    return arg != null ? arg : "0";
});
```

Global placeholders are available without a namespace:

```java
MagicPlaceholders.registerGlobal("server", (audience, arg) -> "Example");
```

Local placeholders are scoped to an owner key:

```java
MagicPlaceholders.registerLocal(this, "balance", (audience, arg) -> "42");
```

## Resolve And Render

Resolve a single entry directly:

```java
String value = MagicPlaceholders.resolve("myplugin", "online", audience, null);
```

Render tokens inside text:

```java
PlaceholderContext context = PlaceholderContext.builder()
    .audience(audience)
    .defaultNamespace("myplugin")
    .ownerKey(this)
    .inline(Map.of("player", "Steve"))
    .build();

String text = "Hello {player}! Balance: {balance|bank}";
String rendered = MagicPlaceholders.render(context, text);
```

Convenience overloads:

```java
String rendered = MagicPlaceholders.render(audience, "Hello {server}");
```

## Resolution Order

For `{key}` tokens:

1. Inline values from the context
2. Local placeholders for the `ownerKey`
3. Default namespace (`defaultNamespace:key`)
4. Global placeholders

Namespaced tokens use `{namespace:key}` or `{namespace:key:arg}`. For local
arguments in plain `{key}` form, use the argument separator (default `|`):

```java
PlaceholderContext context = PlaceholderContext.builder()
    .argumentSeparator("::")
    .build();
```

## Platform Bridges

- Bukkit: the bridge is installed when the Bukkit `Logger` adapter is created
  or when Bukkit bootstrap wiring creates that logger for you.
- Fabric: the bridge is installed by the Fabric logger integration and registers
  available placeholders with PB4 Placeholder API, Text Placeholder API, and
  MiniPlaceholders when those mods are present.

Supported Fabric mods:

- [PB4 Placeholder API](https://placeholders.pb4.eu/)
- [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
- [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)

## Registry Introspection

Snapshots of the registry are available for tooling and debugging:

```java
Set<String> namespaces = MagicPlaceholders.namespaces();
Map<MagicPlaceholders.PlaceholderKey, MagicPlaceholders.PlaceholderResolver> entries =
        MagicPlaceholders.entries();
```

See [Placeholders Advanced](placeholders-advanced.md) for metadata, listeners,
and debug hooks.
