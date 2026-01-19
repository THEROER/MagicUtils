# Quickstart

This page shows minimal wiring for the core modules. Each module can be used
independently, but the order below keeps config + logger + lang consistent.

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

        logger.setLanguageManager(languageManager);
        Messages.register(getName(), languageManager);

        CommandRegistry.initialize(this, "myplugin", logger);
        CommandRegistry.register(this, new HelpCommand(logger));
        CommandRegistry.register(this, new ExampleCommand());
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

            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                CommandRegistry.register("mymod", dispatcher, new HelpCommand(logger));
                CommandRegistry.register("mymod", dispatcher, new ExampleCommand());
            });
        });
    }
}
```

## NeoForge

```java
public final class MyMod {
    private ConfigManager configManager;
    private LoggerCore logger;

    public void onServerStarting() {
        Platform platform = new NeoForgePlatformProvider();
        configManager = new ConfigManager(platform);

        logger = new LoggerCore(platform, configManager, this, "MyMod");
        logger.info().send("<green>Ready.</green>");

        CommandRegistry.initialize("mymod", "mymod", logger);
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistry.register("mymod", event.getDispatcher(), new HelpCommand(logger));
    }
}
```

## Next steps

- Pick the modules you need from the Modules section.
