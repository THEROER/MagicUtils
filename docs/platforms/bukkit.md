# Bukkit/Paper

Use `magicutils-bukkit` to wire MagicUtils to Bukkit/Paper.

## Bootstrap

```java
Platform platform = new BukkitPlatformProvider(plugin);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, plugin, configManager);
```

## Commands

```java
CommandRegistry.initialize(plugin, "myplugin", logger);
CommandRegistry.register(new HelpCommand(logger));
CommandRegistry.register(new ExampleCommand());
```

`CommandRegistry` registers commands directly with the Bukkit CommandMap, so
you do not need to declare them in `plugin.yml`. Avoid duplicating command
entries to prevent conflicts.

## Permissions

Permission nodes are resolved using the prefix passed to `initialize(...)`.
You can still declare explicit permissions via `@Permission` annotations.

## Placeholders

When [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) is installed,
MagicUtils placeholders are exposed automatically via the Bukkit logger adapter.
