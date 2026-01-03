# NeoForge

NeoForge support focuses on platform wiring, config, and logging.

```java
Platform platform = new NeoForgePlatformProvider();
ConfigManager configManager = new ConfigManager(platform);
LoggerCore logger = new LoggerCore(platform, configManager, this, "MyMod");
```

Commands and placeholders are not wired on NeoForge yet. Use MagicUtils core
modules where applicable and handle command registration separately.
