# NeoForge

NeoForge support includes platform wiring, config, logging, and Brigadier commands.

```java
Platform platform = new NeoForgePlatformProvider();
ConfigManager configManager = new ConfigManager(platform);
LoggerCore logger = new LoggerCore(platform, configManager, this, "MyMod");
CommandRegistry.initialize("mymod", "mymod", logger);
```

Register commands via `RegisterCommandsEvent` and the Brigadier dispatcher:

```java
@SubscribeEvent
public void onRegisterCommands(RegisterCommandsEvent event) {
    CommandRegistry.register("mymod", event.getDispatcher(), new HelpCommand(logger));
}
```

There is no NeoForge placeholder bridge yet. Use MagicUtils core placeholders
directly where needed.
