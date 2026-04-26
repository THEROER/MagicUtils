# Bukkit/Paper

Use `magicutils-bukkit` to wire MagicUtils to Bukkit/Paper.

If you want a shared install for multiple plugins, use
`magicutils-bukkit-bundle` as a standalone plugin and add a dependency on it
in your `plugin.yml` (`depend: [MagicUtils]` or `softdepend`).

## Recommended Bootstrap

```java
public final class MyPlugin extends JavaPlugin {
    private BukkitBootstrap.RuntimeResult magic;

    @Override
    public void onEnable() {
        magic = BukkitBootstrap.forPlugin(this)
                .enableCommands()
                .configureCommands(registry -> registry.registerCommand(new ExampleCommand()))
                .buildRuntime();
    }

    @Override
    public void onDisable() {
        if (magic != null) {
            magic.runtime().close();
            magic = null;
        }
    }
}
```

`build()` returns the legacy bootstrap view. `buildRuntime()` additionally gives
you a managed `MagicRuntime`.

## Manual Wiring

```java
Platform platform = new BukkitPlatformProvider(plugin);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, plugin, configManager);
```

Use the manual path only when you need full control over how the services are
created.

## Commands

`BukkitBootstrap.enableCommands()` creates a `CommandRegistry` for the plugin.
The registry registers commands directly with the Bukkit `CommandMap`, so you do
not need to declare them in `plugin.yml`.

Permission nodes are resolved using the prefix passed to
`permissionPrefix(...)` or, by default, the plugin name.

## Placeholders

When [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) is installed,
MagicUtils placeholders can be bridged into PlaceholderAPI through the Bukkit
integration layer.
