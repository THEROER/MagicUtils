# Fabric

For most mods, use `magicutils-fabric-bundle` and wire the runtime through
`FabricBootstrap`.

## Recommended Bootstrap

```java
public final class MyMod implements ModInitializer {
    private static final String MOD_ID = "mymod";

    private MinecraftServer server;
    private FabricBootstrap.RuntimeResult magic;

    @Override
    public void onInitialize() {
        magic = FabricBootstrap.forMod(MOD_ID, () -> server)
                .enableCommands()
                .buildRuntime();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (magic.commandRegistry() != null) {
                magic.commandRegistry().registerCommand(dispatcher, new ExampleCommand());
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            this.server = null;
            if (magic != null) {
                magic.runtime().close();
                magic = null;
            }
        });
    }
}
```

`FabricBootstrap` wires `Platform`, `ConfigManager`, `Logger`,
`LanguageManager`, `Messages`, and an optional Fabric command registry. Actual
command registration still happens in Fabric's Brigadier callback.

## Modular Setup

If you do not want the bundle, combine the platform adapter with the Fabric
integration modules you need:

- `magicutils-fabric`
- `magicutils-commands-fabric`
- `magicutils-logger-fabric`
- `magicutils-placeholders-fabric`

## Permissions

Fabric permissions integrate with `fabric-permissions-api-v0` when installed.
If no permission provider exists, MagicUtils falls back to op-level checks. Use
`opLevel(...)` on `FabricBootstrap` or `CommandRegistry.create(...)` when you
need a different default operator level.

## Placeholders

Fabric placeholder support is optional and activates automatically when
placeholder mods are present:

- [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
- [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)

## Config Format

Default config format on Fabric is JSONC. You can override it via
`<config>.format` or `magicutils.format` (see the Config module docs).

## Bundle Strategy

- Embed `magicutils-fabric-bundle` in your mod.
- Or ship one shared bundle mod on the server and depend on it.

Do not use both approaches at the same time.
