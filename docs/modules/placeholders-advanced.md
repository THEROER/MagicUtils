# Placeholders Advanced

This page covers namespace metadata, listeners, local scopes, and platform
integration details.

## Namespace Metadata

Register namespace metadata to expose author and version information:

```java
MagicPlaceholders.registerNamespace("donatemenu", "THEROER", "1.10.0");
```

Metadata is consumed by PlaceholderAPI expansions and Fabric namespace
registration layers.

## Key Normalization

Namespaces and keys are normalized to lowercase. Use lowercase names to avoid
accidental duplicates.

## Arguments

Resolvers receive an optional `argument` string.

PlaceholderAPI-style examples:

- `%donatemenu_balance%`
- `%donatemenu_balance:bank%`

Inside `MagicPlaceholders.render(...)`, the default argument separator is `|`:

- `{balance|bank}`
- `{donatemenu:balance}`
- `{donatemenu:balance:bank}`

Override the local separator per context when needed:

```java
PlaceholderContext context = PlaceholderContext.builder()
    .argumentSeparator("::")
    .build();
```

## Audience Handling

`Audience` can be `null` for console or offline contexts. Use
`MagicPlaceholders.audienceFromUuid(UUID)` when you only have a player ID:

```java
MagicPlaceholders.register("donatemenu", "balance", (audience, arg) -> {
    if (audience == null) {
        return "0";
    }
    return "42";
});
```

## Local And Global Placeholders

Global placeholders are namespace-free:

```java
MagicPlaceholders.registerGlobal("server", (audience, arg) -> "Example");
```

Local placeholders are tied to an owner key such as a plugin instance, a
logger, or another stable runtime object:

```java
MagicPlaceholders.registerLocal(this, "balance", (audience, arg) -> "42");
```

This is useful when you want one placeholder key to mean different things in
different runtime scopes.

## Registry Listeners

Listen to registry changes:

```java
MagicPlaceholders.addListener(new MagicPlaceholders.PlaceholderListener() {
    public void onPlaceholderRegistered(MagicPlaceholders.PlaceholderKey key) {
        // refresh cache, update UI, etc.
    }

    public void onPlaceholderUnregistered(MagicPlaceholders.PlaceholderKey key) {
    }

    public void onNamespaceUpdated(String namespace) {
    }
});
```

## Debug Listeners

Subscribe to resolution events:

```java
MagicPlaceholders.addDebugListener((ownerKey, key, audience, arg, result) -> {
    // inspect result.value()
});
```

This is especially useful when debugging placeholder chains inside logger
output.

## Safe Resolution

`MagicPlaceholders.resolve(...)` returns an empty string when the placeholder is
missing or when a resolver throws. Keep resolvers fast and side-effect free.

## Integration Summary

- Bukkit: auto-bridges to [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi)
  when the Bukkit logger integration is active.
- Fabric: registers with [PB4 Placeholder API](https://placeholders.pb4.eu/)
  when present.
- Fabric: registers with [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
  and [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders) when present.
