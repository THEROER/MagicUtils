# FAQ / Troubleshooting

Common issues and fixes when integrating MagicUtils.

## YAML/TOML config not available

If you see `YAMLFactory`/`TOMLFactory` missing, add the format helpers:

- `magicutils-config-yaml`
- `magicutils-config-toml`

If the config format is not required, switch to JSON/JSONC instead.

## Jackson `NoSuchMethodError`

Errors like `YAMLParser.createChildObjectContext(...)` or
`BufferRecycler.releaseToPool()` indicate mixed Jackson versions. Make sure
your project uses the same Jackson version as MagicUtils (currently `2.17.1`)
and exclude older transitive Jackson artifacts.

## `NoClassDefFoundError: dev.ua.theroer.magicutils.platform.Platform`

The platform API is missing from the runtime. Add `magicutils-api` (or use a
platform bundle such as `magicutils-bukkit` / `magicutils-fabric-bundle`).

## `NoClassDefFoundError` for Adventure classes

Missing classes like `net.kyori.ansi.StyleOps` or
`net.kyori.adventure.text.serializer.json.JSONComponentSerializer` mean the
Adventure serializer dependencies were not included. Use the platform bundle
or add the required Adventure modules explicitly.

## Placeholders do not resolve

MagicUtils bridges to external placeholder mods/plugins only when they are
installed:

- Bukkit: [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi)
- Fabric: [PB4 Placeholder API](https://placeholders.pb4.eu/)
- Fabric: [Text Placeholder API](https://modrinth.com/mod/placeholder-api) or
  [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)

## Which artifact should I use?

For most users:

- Bukkit/Paper plugin: `magicutils-bukkit`
- Fabric mod: `magicutils-fabric-bundle`
- Velocity plugin: `magicutils-velocity`
- NeoForge mod: `magicutils-neoforge` plus `magicutils-commands-neoforge`

Use the modular artifacts only when you intentionally want custom wiring.

## Permissions on Fabric

Without `fabric-permissions-api`, MagicUtils falls back to op-level checks.
Install the permissions API (and a permissions plugin like LuckPerms) to get
full permission support.

## Do I need to call `magic.runtime().close()`?

Yes, if you built your services through `buildRuntime()` and your platform has a
clear shutdown phase. Closing the runtime unregisters hooks, closes managed
resources, and shuts down the config manager when it owns it.

## Why do Fabric commands still register in `CommandRegistrationCallback`?

Because Brigadier registration still belongs to Fabric's command callback.
`FabricBootstrap` prepares the shared services and command registry, but the
dispatcher itself only exists inside the event callback.

## `Logger` or `LoggerCore`?

Use:

- `Logger` on Bukkit and Fabric
- `LoggerCore` on Velocity, NeoForge, and custom platforms

Bootstrap helpers return the correct logger type for each platform.

## Config watcher warnings

If you see `Failed to register config watcher` / `inotify` warnings, your OS
file watch limit is too low. MagicUtils disables realtime reload in this
case. Increase the limit or call `ConfigManager.shutdown()` on plugin disable
to free watchers.
