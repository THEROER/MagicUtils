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
- Fabric: [Text Placeholder API](https://modrinth.com/mod/placeholder-api) or
  [MiniPlaceholders](https://modrinth.com/mod/miniplaceholders)

## Permissions on Fabric

Without `fabric-permissions-api`, MagicUtils falls back to op-level checks.
Install the permissions API (and a permissions plugin like LuckPerms) to get
full permission support.

## Config watcher warnings

If you see `Failed to register config watcher` / `inotify` warnings, your OS
file watch limit is too low. MagicUtils disables realtime reload in this
case. Increase the limit or call `ConfigManager.shutdown()` on plugin disable
to free watchers.
