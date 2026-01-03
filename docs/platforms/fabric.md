# Fabric

Fabric uses `FabricPlatformProvider` and the jar-in-jar bundle.

## Bootstrap

```java
Platform platform = new FabricPlatformProvider(server);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, configManager, "MyMod");
```

## Commands

```java
CommandRegistry.initialize("mymod", "mymod", logger);
CommandRegistry.register(new HelpCommand(logger));
CommandRegistry.register(new ExampleCommand());
```

You can pass a custom op level via `CommandRegistry.initialize(..., opLevel)`.

## Permissions

Fabric permissions integrate with `fabric-permissions-api-v0` when installed.
If no permission provider exists, MagicUtils falls back to op-level checks.

## Config format

Default config format on Fabric is JSONC. You can override it via
`<config>.format` or `magicutils.format` (see Config module docs).

## Bundle strategy

- Embed `magicutils-fabric-bundle` in your mod (recommended).
- Or ship one shared bundle mod on the server and depend on it.

Do not use both approaches at the same time.
