# Logger

The logger module wraps Adventure components and provides a clean API for
console and chat output, with optional localisation.

## Key classes

- `Logger` (platform adapter)
- `PrefixedLogger` (sub-logger with its own prefix)
- `LoggerConfig` (`logger.yml`)

## Example

```java
Platform platform = new BukkitPlatformProvider(plugin);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, plugin, configManager);

logger.info("<green>Ready.</green>");
PrefixedLogger db = logger.withPrefix("database", "[DB]");
db.warn("Slow query detected");
```

## Tips

- Use `logger.withPrefix("name")` to create per-feature loggers.
- For Fabric, pass the mod name into the logger constructor so the console
  prefix shows your mod name instead of the default.
