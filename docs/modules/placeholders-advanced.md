# Placeholders advanced

This page covers metadata, registry events, and argument handling for
MagicUtils placeholders.

## Namespace metadata

Register namespaces to expose author/version data to integrations:

```java
MagicPlaceholders.registerNamespace("donatemenu", "THEROER", "1.10.0");
```

Metadata is used by PlaceholderAPI expansions and MiniPlaceholders namespace
registration.

## Key normalization

Namespaces and keys are normalized to lowercase. Use consistent lowercase
names to avoid accidental duplicates.

## Arguments

Resolvers receive an optional `argument` string (the part after the first `:`).

PlaceholderAPI example:

- `%donatemenu_balance%`
- `%donatemenu_balance:bank%`

On Fabric, the argument is supplied by the active placeholder mod. Use the
syntax required by [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
or [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders).

For `{key|arg}` rendering inside MagicPlaceholders, the `|` separator is used
by default. You can override it per context:

```java
PlaceholderContext context = PlaceholderContext.builder()
    .argumentSeparator("::")
    .build();
```

## Audience handling

`Audience` can be `null` (console or offline users). Use
`MagicPlaceholders.audienceFromUuid(UUID)` when you only have an ID.

```java
MagicPlaceholders.register("donatemenu", "balance", (audience, arg) -> {
    if (audience == null) {
        return "0";
    }
    return "42";
});
```

## Registry listeners

Listen to registry changes:

```java
MagicPlaceholders.addListener(new MagicPlaceholders.PlaceholderListener() {
    public void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key) {
        // update UI, refresh cache, etc.
    }

    public void onPlaceholderUnregistered(MagicPlaceholders.PlaceholderKey key) {
    }

    public void onNamespaceUpdated(String namespace) {
    }
});
```

## Local and global placeholders

Global placeholders work without a namespace:

```java
MagicPlaceholders.registerGlobal("server", (audience, arg) -> "Example");
```

Local placeholders are scoped to an owner key (plugin/mod instance, a logger,
or any stable object):

```java
MagicPlaceholders.registerLocal(this, "balance", (audience, arg) -> "42");
```

## Resolution order

For `{key}` tokens rendered via `MagicPlaceholders.render(...)`:

1. Inline values supplied by `PlaceholderContext`.
2. Local placeholders for the `ownerKey`.
3. Default namespace (`defaultNamespace:key`).
4. Global placeholders.

For `{namespace:key(:arg)}` tokens, only the namespaced registry is used.

## Safe resolution

`MagicPlaceholders.resolve(...)` returns an empty string when the placeholder is
missing or when a resolver throws. Keep resolvers fast and side-effect free.

## Debug listeners

Subscribe to resolution events:

```java
MagicPlaceholders.addDebugListener((ownerKey, key, audience, arg, result) -> {
    // log or inspect result.value()
});
```

## Integration summary

- Bukkit: auto-bridges to [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi)
  when installed.
- Fabric: registers with [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
  and [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders) when present.
