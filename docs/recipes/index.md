# Recipes

## Embed MagicUtils in a Fabric mod

Use `include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}")` and avoid
installing the standalone bundle on the server.

## Share a single bundle on a server

Install `magicutils-fabric-bundle` in `mods/` and use `modImplementation` only.
