# NeoForge

NeoForge support currently uses the manual wiring path with
`magicutils-neoforge` and the optional `magicutils-commands-neoforge` module.

## Platform Wiring

```java
public final class MyMod {
    private static final String MOD_ID = "mymod";

    private final Platform platform;
    private final ConfigManager configManager;
    private final LoggerCore logger;
    private final CommandRegistry commands;

    public MyMod() {
        platform = new NeoForgePlatformProvider();
        configManager = new ConfigManager(platform);
        logger = new LoggerCore(platform, configManager, this, "MyMod");
        commands = CommandRegistry.create(MOD_ID, MOD_ID, logger);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        commands.registerCommand(event.getDispatcher(), new ExampleCommand());
    }
}
```

If you need a different operator threshold, use
`CommandRegistry.create(..., opLevel)`.

## Notes

- NeoForge uses `LoggerCore` directly instead of the Bukkit/Fabric `Logger`
  wrapper.
- There is no dedicated NeoForge bootstrap helper yet.
- There is no NeoForge placeholder bridge yet. Use MagicUtils core
  placeholders directly where needed.
