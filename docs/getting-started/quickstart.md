# Quickstart

This page shows minimal wiring for the core modules. Each module can be used
independently.

## Bukkit/Paper

```java
public final class MyPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private Logger logger;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        Platform platform = new BukkitPlatformProvider(this);
        configManager = new ConfigManager(platform);
        logger = new Logger(platform, this, configManager);

        languageManager = new LanguageManager(this, configManager);
        languageManager.init("en");
        languageManager.addMagicUtilsMessages();

        CommandRegistry.initialize(this, "myplugin", logger);
        CommandRegistry.register(new ExampleCommand(languageManager, this));
    }
}
```

## Fabric

```java
public final class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Platform platform = new FabricPlatformProvider(server);
            ConfigManager configManager = new ConfigManager(platform);
            Logger logger = new Logger(platform, configManager, "MyMod");

            CommandRegistry.initialize("mymod", "mymod", logger);
            CommandRegistry.register(new ExampleCommand());
        });
    }
}
```

## Next steps

- Pick the modules you need from the Modules section.
- Review the examples under `examples/` in the repository.
