# Fabric

Fabric uses `FabricPlatformProvider` and the jar-in-jar bundle.

```java
Platform platform = new FabricPlatformProvider(server);
ConfigManager configManager = new ConfigManager(platform);
Logger logger = new Logger(platform, configManager, "MyMod");
```

Notes:

- Default config format is JSONC.
- Use the bundle as either a shared server mod or embed it inside your mod.
- Do not use both approaches at the same time.
