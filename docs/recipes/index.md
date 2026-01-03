# Recipes

## Embed MagicUtils in a Fabric mod

Use `include("dev.ua.theroer:magicutils-fabric-bundle:{{ magicutils_version }}")` and avoid
installing the standalone bundle on the server.

## Share a single bundle on a server

Install `magicutils-fabric-bundle` in `mods/` and use `modImplementation` only.

Download link:
[`magicutils-fabric-bundle-{{ magicutils_version }}.jar`](https://theroer.github.io/MagicUtils/maven/dev/ua/theroer/magicutils-fabric-bundle/{{ magicutils_version }}/magicutils-fabric-bundle-{{ magicutils_version }}.jar)

## Register the built-in help command

```java
CommandRegistry.register(new HelpCommand(logger));
```

## Add help as a subcommand

```java
CommandRegistry.register(new MyCommand()
        .addSubCommand(HelpCommandSupport.createHelpSubCommand(
                "help",
                logger.getCore(),
                CommandRegistry::getCommandManager
        )));
```

## Force a config format

Create `<config>.format` next to the file or `magicutils.format` in the root
config directory with a single line:

```
jsonc
```

## Add enum suggestions

Enum parameters are auto-suggested. You can additionally limit the visible
choices via `@Suggest("{one,two}")` for a specific argument.
