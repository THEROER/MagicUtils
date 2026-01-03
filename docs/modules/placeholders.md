# Placeholders

The placeholder module offers a shared registry and optional PAPI integration.

## Example

```java
MagicPlaceholders.register("myplugin", "online", ctx -> "42");
```

Use `MagicPlaceholders.resolve("myplugin", "online", ctx)` to resolve a value
manually.
