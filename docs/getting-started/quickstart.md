# Quickstart

The recommended path is bootstrap-first: use the platform bootstrap helper when
it exists, keep the returned runtime handle, and close it when the platform
shuts down.

## Bukkit/Paper

```java
public final class MyPlugin extends JavaPlugin {
    private BukkitBootstrap.RuntimeResult magic;

    @Override
    public void onEnable() {
        magic = BukkitBootstrap.forPlugin(this)
                .enableCommands()
                .configureCommands(registry -> registry.registerCommand(new ExampleCommand()))
                .buildRuntime();

        magic.logger().info("Ready.");
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

`BukkitBootstrap` wires `Platform`, `ConfigManager`, `Logger`,
`LanguageManager`, `Messages`, and an optional `CommandRegistry`.

## Fabric

```java
public final class MyMod implements ModInitializer {
    private static final String MOD_ID = "mymod";

    private MinecraftServer server;
    private FabricBootstrap.RuntimeResult magic;

    @Override
    public void onInitialize() {
        magic = FabricBootstrap.forMod(MOD_ID, () -> server)
                .enableCommands()
                .buildRuntime();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (magic.commandRegistry() != null) {
                magic.commandRegistry().registerCommand(dispatcher, new ExampleCommand());
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            this.server = null;
            if (magic != null) {
                magic.runtime().close();
                magic = null;
            }
        });
    }
}
```

`FabricBootstrap` sets up the shared services early, while actual command
registration still happens inside Fabric's Brigadier callback.

## Velocity

```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public final class MyPlugin {
    private final ProxyServer proxy;
    private final org.slf4j.Logger slf4j;
    private final Path dataDirectory;
    private VelocityBootstrap.RuntimeResult magic;

    @Inject
    public MyPlugin(ProxyServer proxy,
                    org.slf4j.Logger slf4j,
                    @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.slf4j = slf4j;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        magic = VelocityBootstrap.forPlugin(proxy, this, "MyPlugin", dataDirectory)
                .slf4j(slf4j)
                .enableCommands()
                .configureCommands(registry -> registry.registerCommand(new ExampleCommand()))
                .buildRuntime();

        magic.logger().info("Ready.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (magic != null) {
            magic.runtime().close();
            magic = null;
        }
    }
}
```

`VelocityBootstrap` creates a managed `LoggerCore`, registers shutdown cleanup,
and can wire the Velocity command registry for you.

## NeoForge

NeoForge currently uses the manual wiring path.

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

        logger.info("Ready.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        commands.registerCommand(event.getDispatcher(), new ExampleCommand());
    }
}
```

## Using The Runtime Handle

Every `buildRuntime()` call returns `MagicRuntime`, which exposes the shared
services as typed components:

```java
MagicRuntime runtime = magic.runtime();
ConfigManager configManager = runtime.requireComponent(ConfigManager.class);
LoggerCore logger = runtime.requireComponent(LoggerCore.class);
```

Use named resources and config bindings when you want runtime-managed clients or
other reloadable services.

## Next Steps

- Pick the modules you need from the Modules section.
- Use the platform pages for more detailed bootstrap notes.
- Read the Runtime guide for `MagicRuntime`, managed resources, and config bindings.
- Use the Migration guide if you are moving from older manual wiring examples.
- See the HTTP client page for `MagicRuntime`-bound profiles.
