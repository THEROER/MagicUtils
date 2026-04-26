# Recipes

## Embed MagicUtils In A Fabric Mod

Use `include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}")`
and avoid installing the standalone bundle on the server.

## Share A Single Bundle On A Server

Install `magicutils-fabric-bundle` in `mods/` and use `modImplementation`
without `include(...)`.

Download link:
[`magicutils-fabric-bundle-{{ magicutils_version }}.jar`](https://theroer.github.io/MagicUtils/maven/dev/ua/theroer/magicutils-fabric-bundle/{{ magicutils_version }}/magicutils-fabric-bundle-{{ magicutils_version }}.jar)

## Register The Built-In Help Command

Bukkit or Fabric:

```java
registry.registerCommand(new HelpCommand(logger, registry));
```

## Add Help As A Subcommand

Use this pattern when you want help attached to an existing command tree or on
platforms without a dedicated `HelpCommand` wrapper:

```java
registry.registerCommand(new MyCommand()
        .addSubCommand(HelpCommandSupport.createHelpSubCommand(
                "help",
                loggerCore,
                registry::commandManager
        )));
```

## Force A Config Format

Create `<config>.format` next to the file or `magicutils.format` in the root
config directory with a single line:

```text
jsonc
```

## Add Enum Suggestions

Enum parameters are auto-suggested. You can additionally limit visible choices
via `@Suggest("{one,two}")` for a specific argument.
