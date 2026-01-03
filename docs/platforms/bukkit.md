# Bukkit/Paper

Use `BukkitPlatformProvider` to wire MagicUtils to Bukkit/Paper.

```java
Platform platform = new BukkitPlatformProvider(plugin);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, plugin, configManager);
```

`CommandRegistry` and `Logger` live in the Bukkit adapter, so add
`magicutils-bukkit` as a dependency.
